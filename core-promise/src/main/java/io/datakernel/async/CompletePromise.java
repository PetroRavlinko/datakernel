/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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

package io.datakernel.async;

import io.datakernel.exception.UncheckedException;
import io.datakernel.functional.Try;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.datakernel.eventloop.Eventloop.getCurrentEventloop;

public abstract class CompletePromise<T> implements MaterializedPromise<T> {
	@Override
	public final boolean isComplete() {
		return true;
	}

	@Override
	public final boolean isResult() {
		return true;
	}

	@Override
	public final boolean isException() {
		return false;
	}

	@Override
	abstract public T getResult();

	@NotNull
	@Override
	public final Throwable getException() {
		throw new UnsupportedOperationException();
	}

	@NotNull
	@Override
	public final <U, S extends BiConsumer<? super T, Throwable> & Promise<U>> Promise<U> then(@NotNull S promise) {
		promise.accept(getResult(), null);
		return promise;
	}

	@NotNull
	@Override
	public final <U> Promise<U> thenApply(@NotNull Function<? super T, ? extends U> fn) {
		try {
			return Promise.of(fn.apply(getResult()));
		} catch (UncheckedException u) {
			return Promise.ofException(u.getCause());
		}
	}

	@NotNull
	@Override
	public final <U> Promise<U> thenApplyEx(@NotNull BiFunction<? super T, Throwable, ? extends U> fn) {
		try {
			return Promise.of(fn.apply(getResult(), null));
		} catch (UncheckedException u) {
			return Promise.ofException(u.getCause());
		}
	}

	@NotNull
	@Override
	public final <U> Promise<U> thenCompose(@NotNull Function<? super T, ? extends Promise<U>> fn) {
		try {
			return fn.apply(getResult());
		} catch (UncheckedException u) {
			return Promise.ofException(u.getCause());
		}
	}

	@NotNull
	@Override
	public final <U> Promise<U> thenComposeEx(@NotNull BiFunction<? super T, Throwable, ? extends Promise<U>> fn) {
		try {
			return fn.apply(getResult(), null);
		} catch (UncheckedException u) {
			return Promise.ofException(u.getCause());
		}
	}

	@NotNull
	@Override
	public final Promise<T> whenComplete(@NotNull BiConsumer<? super T, Throwable> action) {
		action.accept(getResult(), null);
		return this;
	}

	@NotNull
	@Override
	public final Promise<T> whenResult(@NotNull Consumer<? super T> action) {
		action.accept(getResult());
		return this;
	}

	@NotNull
	@Override
	public final Promise<T> whenException(@NotNull Consumer<Throwable> action) {
		return this;
	}

	@NotNull
	@SuppressWarnings("unchecked")
	@Override
	public final <U, V> Promise<V> combine(@NotNull Promise<? extends U> other, @NotNull BiFunction<? super T, ? super U, ? extends V> fn) {
		if (other instanceof CompletePromise) {
			return Promise.of(fn.apply(getResult(), ((CompletePromise<U>) other).getResult()));
		}
		return other.thenApply(otherResult -> fn.apply(getResult(), otherResult));
	}

	@NotNull
	@Override
	public final Promise<Void> both(@NotNull Promise<?> other) {
		if (other instanceof CompletePromise) {
			return Promise.complete();
		}
		return other.toVoid();
	}

	@NotNull
	@Override
	public final Promise<T> either(@NotNull Promise<? extends T> other) {
		return this;
	}

	@NotNull
	@Override
	public final MaterializedPromise<T> async() {
		SettablePromise<T> result = new SettablePromise<>();
		getCurrentEventloop().post(() -> result.set(getResult()));
		return result;
	}

	@NotNull
	@Override
	public final Promise<Try<T>> toTry() {
		return Promise.of(Try.of(getResult()));
	}

	@NotNull
	@Override
	public final Promise<Void> toVoid() {
		return Promise.complete();
	}

	@NotNull
	@Override
	public final CompletableFuture<T> toCompletableFuture() {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.complete(getResult());
		return future;
	}
}
