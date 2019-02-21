/*
 * Copyright (C) 2015-2019 SoftIndex LLC.
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

package io.datakernel.csp;

import io.datakernel.async.Promise;
import io.datakernel.async.SettableCallback;
import io.datakernel.async.SettablePromise;
import io.datakernel.util.Recyclable;

import java.util.Iterator;

import static io.datakernel.util.Recyclable.deepRecycle;

/**
 * Provides additional functionality for managing {@link ChannelConsumer}s.
 */
public final class ChannelConsumers {
	private ChannelConsumers() {
	}

	/**
	 * Passes iterator's values to the {@code output} until it {@code hasNext()},
	 * then returns a promise of {@code null} as a marker of completion.
	 * <p>
	 * If there was an exception while accepting iterator, a promise of
	 * exception will be returned.
	 *
	 * @param output a {@code ChannelConsumer}, which accepts the iterator
	 * @param it     an {@link Iterator} which provides some values
	 * @param <T>    a data type of passed values
	 * @return a promise of {@code null} as a marker of completion
	 */
	public static <T> Promise<Void> acceptAll(ChannelConsumer<T> output, Iterator<? extends T> it) {
		if (!it.hasNext()) return Promise.complete();
		return Promise.ofCallback(cb -> acceptAllImpl(output, it, cb));
	}

	private static <T> void acceptAllImpl(ChannelConsumer<T> output, Iterator<? extends T> it, SettableCallback<Void> cb) {
		while (it.hasNext()) {
			Promise<Void> accept = output.accept(it.next());
			if (accept.isResult()) continue;
			accept.whenComplete(($, e) -> {
				if (e == null) {
					acceptAllImpl(output, it, cb);
				} else {
					deepRecycle(it);
					cb.setException(e);
				}
			});
			return;
		}
		cb.set(null);
	}

	public static <T extends Recyclable> ChannelConsumer<T> recycling() {
		return new RecyclingChannelConsumer<>();
	}
}
