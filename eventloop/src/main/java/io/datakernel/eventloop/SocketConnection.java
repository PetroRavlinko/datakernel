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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Common abstract class, which represents any kind of connection.
 */
public abstract class SocketConnection implements NioChannelEventHandler {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private static final int DEFAULT_RECEIVE_BUFFER_SIZE = 8 * 1024;

	protected final Eventloop eventloop;

	private SelectionKey key;

	private int ops = SelectionKey.OP_READ;

	protected int receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;

	protected long lifeTime;
	protected long readTime;
	protected long writeTime;

	protected SocketConnection(Eventloop eventloop) {
		this.eventloop = eventloop;
		lifeTime = eventloop.currentTimeMillis();
		readTime = lifeTime;
		writeTime = lifeTime;
	}

	/**
	 * Returns the {@link Eventloop} with which was associated with this connection
	 */
	public final Eventloop getEventloop() {
		return eventloop;
	}

	private void updateInterests(int newOps) {
		ops = newOps;
		if (key != null) {
			key.interestOps(newOps);
		}
	}

	/**
	 * Gets interested operations of channel of this connection.
	 *
	 * @param newOps new interest
	 */
	protected void interests(int newOps) {
		if (ops != newOps) {
			updateInterests(newOps);
		}
	}

	/**
	 * updates interest set of connection's {@link SelectionKey} to be interested or not in reading operations
	 *
	 * @param readInterest tells whether connection's {@link SelectionKey} is interested in read or not
	 */
	protected void readInterest(boolean readInterest) {
		interests(readInterest ? (ops | SelectionKey.OP_READ) : (ops & ~SelectionKey.OP_READ));
	}

	/**
	 * updates interest set of connection's {@link SelectionKey} to be interested or not in writing operations
	 *
	 * @param writeInterest tells whether connection's {@link SelectionKey} is interested in read or not
	 */
	protected void writeInterest(boolean writeInterest) {
		interests(writeInterest ? (ops | SelectionKey.OP_WRITE) : (ops & ~SelectionKey.OP_WRITE));
	}

	protected void onReadException(Exception e) {
		eventloop.recordIoError(e, this);
		close();
	}

	protected void onWriteException(Exception e) {
		eventloop.recordIoError(e, this);
		close();
	}

	/**
	 * Registers channel of this connection in eventloop with which it was related.
	 */
	@SuppressWarnings("MagicConstant")
	public final void register() {
		try {
			key = getChannel().register(eventloop.ensureSelector(), ops, this);
		} catch (IOException e) {
			eventloop.post(new Runnable() {
				@Override
				public void run() {
					closeChannel();
					onClosed();
				}
			});
		}
		onRegistered();
	}

	/**
	 * It called after completing registering this connection.
	 */
	public void onRegistered() {
	}

	/**
	 * It called after closing this connection.
	 */
	public abstract void onClosed();

	/**
	 * Closes current channel.  It can be called only from this connection's eventloop's thread.
	 */
	public void close() {
		assert eventloop.inEventloopThread();
		if (key == null) return;
		closeChannel();
		key = null;
		onClosed();
	}

	private void closeChannel() {
		Channel channel = getChannel();
		if (channel == null)
			return;
		try {
			channel.close();
		} catch (IOException e) {
			eventloop.recordIoError(e, toString());
		}
	}

	protected void onReadEndOfStream() {
		close();
	}

	/**
	 * It called before beginning of reading.
	 */
	public abstract void onReadReady();

	protected void onWriteFlushed() {
	}

	/**
	 * It called before beginning of writing.
	 */
	public abstract void onWriteReady();

	protected abstract SelectableChannel getChannel();

	public final boolean isRegistered() {
		return key != null;
	}

	public final long getLifeTime() {
		return lifeTime;
	}

	public final long getReadTime() {
		return readTime;
	}

	public final long getWriteTime() {
		return writeTime;
	}

	public final long getActivityTime() {
		return Math.max(readTime, writeTime);
	}

	@Override
	public String toString() {
		final long currentTimeMillis = eventloop.currentTimeMillis();
		return getDebugName() + "{" +
				"lifeTime=" + (currentTimeMillis - lifeTime) +
				", readTime=" + (currentTimeMillis - readTime) +
				", writeTime=" + (currentTimeMillis - writeTime) +
				'}';
	}

	protected String getDebugName() {
		return getClass().getSimpleName();
	}

}
