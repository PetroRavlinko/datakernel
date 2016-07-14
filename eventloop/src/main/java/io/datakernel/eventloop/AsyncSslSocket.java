/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.eventloop;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.datakernel.bytebuf.ByteBufQueue;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW;

public final class AsyncSslSocket implements AsyncTcpSocket, AsyncTcpSocket.EventHandler {
	private final Eventloop eventloop;
	private final SSLEngine engine;
	private final Executor executor;
	private final AsyncTcpSocket upstream;

	private AsyncTcpSocket.EventHandler downstreamEventHandler;

	private boolean ignoreIOErrors = false;

	private ByteBuf net2engine;
	private final ByteBufQueue app2engineQueue = new ByteBufQueue();

	private Status status = Status.OPEN;
	private boolean readInterest = false;
	private boolean writeInterest = false;
	private boolean syncPosted = false;
	private boolean flushAndClose = false;

	public AsyncSslSocket(Eventloop eventloop, AsyncTcpSocket asyncTcpSocket, SSLEngine engine, Executor executor) {
		this.eventloop = eventloop;
		this.engine = engine;
		this.executor = executor;
		this.upstream = asyncTcpSocket;
	}

	@Override
	public void onRegistered() {
		status = Status.OPEN;
		downstreamEventHandler.onRegistered();
		try {
			engine.beginHandshake();
			doSync();
		} catch (SSLException e) {
			handleSSLException(e, true);
		}
	}

	@Override
	public void onRead(ByteBuf buf) {
		if (!isOpen()) return;
		if (net2engine == null) {
			net2engine = buf;
		} else {
			net2engine = ByteBufPool.concat(net2engine, buf);
		}
		sync();
	}

	@Override
	public void onShutdownInput() {
		try {
			engine.closeInbound();
			status = Status.CLOSED;
		} catch (SSLException e) {
			status = Status.CLOSED_WITH_ERROR;
			downstreamEventHandler.onClosedWithError(e);
			engine.closeOutbound();  // try to send close_notify
			ignoreIOErrors = true;  // ignore io errors, because downstreamEventHandler was already notified about error
			sync();
		}
	}

	@Override
	public void onWrite() {
		if (status == Status.CLOSING) {
			upstream.close();
			status = Status.CLOSED;
			return;
		}
		if (!isOpen()) return;
		if (app2engineQueue.isEmpty() && writeInterest) {
			writeInterest = false;
			downstreamEventHandler.onWrite();
		}
	}

	@Override
	public void onClosedWithError(Exception e) {
		if (!isOpen() || ignoreIOErrors) return;
		status = Status.CLOSED_WITH_ERROR;
		downstreamEventHandler.onClosedWithError(e);
	}

	@Override
	public void setEventHandler(EventHandler eventHandler) {
		this.downstreamEventHandler = eventHandler;
	}

	@Override
	public void read() {
		if (!isOpen()) return;
		upstream.read();
		readInterest = true;
		postSync();
	}

	private void postSync() {
		if (!syncPosted) {
			syncPosted = true;
			eventloop.post(new Runnable() {
				@Override
				public void run() {
					syncPosted = false;
					sync();
				}
			});
		}
	}

	@Override
	public void write(ByteBuf buf) {
		assert !flushAndClose;

		if (!isOpen()) return;
		app2engineQueue.add(buf);
		writeInterest = true;
		postSync();
	}

	@Override
	public void shutdownOutput() {
		throw new UnsupportedOperationException("SSL cannot work in half-duplex mode");
	}

	@Override
	public void close() {
		eventloop.post(new Runnable() {
			@Override
			public void run() {
				if (!isOpen()) return;
				app2engineQueue.clear();
				engine.closeOutbound();
				status = Status.CLOSING;
				postSync();
			}
		});
	}

	@Override
	public void flushAndClose() {
		flushAndClose = true;
		postSync();
	}

	@Override
	public InetSocketAddress getRemoteSocketAddress() {
		return upstream.getRemoteSocketAddress();
	}

	public boolean isOpen() {
		return status == Status.OPEN;
	}

	private void handleSSLException(final SSLException e, boolean post) {
		if (!isOpen())
			return;
		status = Status.CLOSED_WITH_ERROR;
		upstream.close();
		if (post) {
			eventloop.post(new Runnable() {
				@Override
				public void run() {
					downstreamEventHandler.onClosedWithError(e);
				}
			});
		} else {
			downstreamEventHandler.onClosedWithError(e);
		}
	}

	private SSLEngineResult tryToWriteToApp() throws SSLException {
		ByteBuf dstBuf = ByteBufPool.allocate(engine.getSession().getPacketBufferSize());
		ByteBuffer srcBuffer = net2engine.toByteBufferInReadMode();
		ByteBuffer dstBuffer = dstBuf.toByteBufferInWriteMode();

		SSLEngineResult result;
		try {
			result = engine.unwrap(srcBuffer, dstBuffer);
		} catch (SSLException e) {
			net2engine.recycle();
			dstBuf.recycle();
			throw e;
		}

		net2engine.setReadPosition(srcBuffer.position());
		if (!net2engine.canRead()) {
			net2engine.recycle();
			net2engine = null;
		}

		dstBuf.setWritePosition(dstBuffer.position());
		if (dstBuf.canRead()) {
			downstreamEventHandler.onRead(dstBuf);
		} else {
			dstBuf.recycle();
		}

		return result;
	}

	private SSLEngineResult tryToWriteToNet() throws SSLException {
		ByteBuf sourceBuf = app2engineQueue.takeRemaining();

		ByteBuf dstBuf = ByteBufPool.allocate(engine.getSession().getPacketBufferSize());
		ByteBuffer srcBuffer = sourceBuf.toByteBufferInReadMode();
		ByteBuffer dstBuffer = dstBuf.toByteBufferInWriteMode();

		SSLEngineResult result;
		try {
			result = engine.wrap(srcBuffer, dstBuffer);
		} catch (SSLException e) {
			app2engineQueue.clear();
			dstBuf.recycle();
			throw e;
		}

		sourceBuf.setReadPosition(srcBuffer.position());
		if (sourceBuf.canRead()) {
			app2engineQueue.add(sourceBuf);
		} else {
			sourceBuf.recycle();
		}

		dstBuf.setWritePosition(dstBuffer.position());
		if (dstBuf.canRead()) {
			upstream.write(dstBuf);
		} else {
			dstBuf.recycle();
		}
		return result;
	}

	private void executeTasks() {
		while (true) {
			final Runnable task = engine.getDelegatedTask();
			if (task == null) break;
			executor.execute(new Runnable() {
				@Override
				public void run() {
					task.run();
					eventloop.execute(new Runnable() {
						@Override
						public void run() {
							sync();
						}
					});
				}
			});
		}
	}

	private void sync() {
		try {
			doSync();
		} catch (SSLException e) {
			handleSSLException(e, false);
		}
	}

	@SuppressWarnings("UnusedAssignment")
	private void doSync() throws SSLException {
		SSLEngineResult result;
		while (true) {
			HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
			if (handshakeStatus == NEED_WRAP) {
				result = tryToWriteToNet();
				if (engine.isOutboundDone()) {
					return;
				}
			} else if (handshakeStatus == NEED_UNWRAP) {
				if (net2engine != null) {
					result = tryToWriteToApp();
					if (result.getStatus() == BUFFER_UNDERFLOW) {
						readInterest = true;
						break;
					}
				} else {
					readInterest = true;
					break;
				}
			} else if (handshakeStatus == NEED_TASK) {
				executeTasks();
				return;
			} else if (handshakeStatus == NOT_HANDSHAKING) {
				// read data from net
				if (readInterest && net2engine != null) {
					do {
						result = tryToWriteToApp();
					} while (net2engine != null && result.getStatus() != BUFFER_UNDERFLOW);
				}
				if (engine.isInboundDone()) { // receive close_notify (closing was initiated by other side)
					status = Status.CLOSING;
					engine.closeOutbound();
					downstreamEventHandler.onShutdownInput();

					// other side may have already closed the connection, so we can get "broken pipe" exception
					ignoreIOErrors = true;
				} else {
					// write data to net
					if (writeInterest && app2engineQueue.hasRemaining()) {
						do {
							result = tryToWriteToNet();
						} while (app2engineQueue.hasRemaining());
					}
					if (flushAndClose) {
						engine.closeOutbound();
					} else {
						break;
					}
				}
			} else {
				break;
			}
		}

		if (engine.getHandshakeStatus() == NEED_UNWRAP || readInterest) {
			upstream.read();
		}
	}

	private enum Status {
		OPEN,
		CLOSING,
		CLOSED,
		CLOSED_WITH_ERROR;
	}
}