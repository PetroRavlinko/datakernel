package io.global.db;

import io.datakernel.async.Promise;
import io.datakernel.csp.ChannelConsumer;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.exception.UncheckedException;
import io.global.common.SignedData;
import io.global.db.api.DbStorage;
import io.global.db.util.Utils;
import org.rocksdb.*;

import java.util.concurrent.ExecutorService;

import static io.datakernel.exception.UncheckedException.unchecked;

public class RocksdbDbStorage implements DbStorage {
	private final ExecutorService executor;
	private final RocksDB db;

	private final FlushOptions flushOptions;
	private final WriteOptions writeOptions;

	public RocksdbDbStorage(ExecutorService executor, RocksDB db) {
		this.executor = executor;
		this.db = db;
		flushOptions = new FlushOptions();
		writeOptions = new WriteOptions().setDisableWAL(true);
	}

	public Promise<Void> flush() {
		return Promise.ofRunnable(executor, () -> {
			try {
				db.flush(flushOptions);
			} catch (RocksDBException e) {
				throw new UncheckedException(e);
			}
		});
	}

	private void doPut(SignedData<DbItem> signedDbItem) {
		byte[] key = signedDbItem.getValue().getKey();
		byte[] value = unchecked(() -> db.get(key));
		if (value != null) {
			SignedData<DbItem> old = unchecked(() -> Utils.unpackValue(key, value));
			if (old.getValue().getTimestamp() > signedDbItem.getValue().getTimestamp()) {
				return;
			}
		}
		unchecked(() -> db.put(writeOptions, key, Utils.packValue(signedDbItem)));
	}

	private SignedData<DbItem> doGet(byte[] key) {
		return unchecked(() -> Utils.unpackValue(key, db.get(key)));
	}

	@Override
	public Promise<ChannelConsumer<SignedData<DbItem>>> upload() {
		return Promise.of(ChannelConsumer.<SignedData<DbItem>>of(signedDbItem -> Promise.ofRunnable(executor, () -> doPut(signedDbItem)))
				.withAcknowledgement(ack -> ack.thenCompose($ -> flush())));
	}

	private Promise<RocksIterator> iterator() {
		return Promise.ofCallable(executor, () -> {
			RocksIterator iterator = db.newIterator();
			iterator.seekToFirst();
			return iterator;
		});
	}

	@Override
	public Promise<ChannelSupplier<SignedData<DbItem>>> download(long timestamp) {
		return iterator().thenApply(iterator ->
				ChannelSupplier.of(() ->
						Promise.ofCallable(executor, () -> {
							while (iterator.isValid()) {
								byte[] key = iterator.key();
								SignedData<DbItem> signedDbItem = doGet(key);
								iterator.next();
								if (signedDbItem.getValue().getTimestamp() > timestamp) {
									return signedDbItem;
								}
							}
							return null;
						})));
	}

	@Override
	public Promise<ChannelSupplier<SignedData<DbItem>>> download() {
		return iterator().thenApply(iterator ->
				ChannelSupplier.of(() ->
						Promise.ofCallable(executor, () -> {
							if (!iterator.isValid()) {
								return null;
							}
							SignedData<DbItem> signedDbItem = doGet(iterator.key());
							iterator.next();
							return signedDbItem;
						})));
	}

	@Override
	public Promise<ChannelConsumer<SignedData<byte[]>>> remove() {
		return Promise.of(ChannelConsumer.<SignedData<byte[]>>of(key -> Promise.ofRunnable(executor, () -> unchecked(() -> db.delete(key.getValue()))))
				.withAcknowledgement(ack -> ack.thenCompose($ -> flush())));
	}

	@Override
	public Promise<SignedData<DbItem>> get(byte[] key) {
		return Promise.ofCallable(executor, () -> doGet(key));
	}

	@Override
	public Promise<Void> put(SignedData<DbItem> item) {
		return Promise.ofRunnable(executor, () -> doPut(item));
	}

	@Override
	public Promise<Void> remove(SignedData<byte[]> key) {
		return Promise.ofRunnable(executor, () -> unchecked(() -> db.delete(key.getValue())));
	}
}
