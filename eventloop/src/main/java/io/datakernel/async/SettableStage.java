package io.datakernel.async;

import io.datakernel.annotation.Nullable;
import io.datakernel.exception.StacklessException;
import io.datakernel.functional.Try;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.datakernel.eventloop.Eventloop.getCurrentEventloop;

/**
 * Stage that can be completed or completedExceptionally manually.
 * <p>Can be used as root stage to start execution of chain of stages or when you want wrap your actions in {@code Stage}</p>
 *
 * @param <T> Result type
 */
public final class SettableStage<T> extends AbstractStage<T> implements MaterializedStage<T> {
	private static final Throwable NO_EXCEPTION = new StacklessException();

	@SuppressWarnings("unchecked")
	@Nullable
	protected T result;

	@Nullable
	protected Throwable exception = NO_EXCEPTION;

	public SettableStage() {
	}

	public static <T> SettableStage<T> ofStage(Stage<T> stage) {
		SettableStage<T> result = new SettableStage<>();
		stage.whenComplete(result::set);
		return result;
	}

	@Override
	public boolean isComplete() {
		return exception != NO_EXCEPTION;
	}

	@Override
	public boolean isResult() {
		return exception == null;
	}

	@Override
	public boolean isException() {
		return super.isException();
	}

	@Override
	public boolean hasResult() {
		return isResult();
	}

	@Override
	public boolean hasException() {
		return isException();
	}

	/**
	 * Sets the result of this {@code SettableStage} and completes it.
	 * <p>AssertionError is thrown when you try to set result for  already completed stage.</p>
	 */
	public void set(@Nullable T result) {
		assert !isComplete();
		this.result = result;
		this.exception = null;
		complete(result);
	}

	public void set(@Nullable T result, @Nullable Throwable throwable) {
		if (throwable == null) {
			set(result);
		} else {
			setException(throwable);
		}
	}

	/**
	 * Sets exception and completes this {@code SettableStage} exceptionally.
	 * <p>AssertionError is thrown when you try to set exception for  already completed stage.</p>
	 *
	 * @param throwable exception
	 */
	public void setException(Throwable throwable) {
		assert !isComplete();
		result = null;
		exception = throwable;
		completeExceptionally(throwable);
	}

	/**
	 * The same as {@link SettableStage#trySet(Object, Throwable)} )} but for result only.
	 */
	public void trySet(@Nullable T result) {
		if (isComplete()) {
			return;
		}
		set(result);
	}

	/**
	 * Tries to set result or exception for this {@code SettableStage} if it not yet set.
	 * <p>Otherwise do nothing</p>
	 *
	 * @return {@code true} if result or exception was set, {@code false} otherwise
	 */
	public void trySet(@Nullable T result, @Nullable Throwable throwable) {
		if (isComplete()) {
			return;
		}
		if (throwable == null) {
			trySet(result);
		} else {
			trySetException(throwable);
		}
	}

	/**
	 * The same as {@link SettableStage#trySet(Object, Throwable)} )} but for exception only.
	 */
	public void trySetException(Throwable throwable) {
		if (isComplete()) {
			return;
		}
		setException(throwable);
	}

	public void post(@Nullable T result) {
		getCurrentEventloop().post(() -> set(result));
	}

	public void postException(Throwable throwable) {
		getCurrentEventloop().post(() -> setException(throwable));
	}

	public void post(@Nullable T result, @Nullable Throwable throwable) {
		getCurrentEventloop().post(() -> set(result, throwable));
	}

	public void tryPost(@Nullable T result) {
		getCurrentEventloop().post(() -> trySet(result));
	}

	public void tryPostException(Throwable throwable) {
		getCurrentEventloop().post(() -> trySetException(throwable));
	}

	public void tryPost(@Nullable T result, @Nullable Throwable throwable) {
		getCurrentEventloop().post(() -> trySet(result, throwable));
	}

	@Override
	protected void subscribe(BiConsumer<? super T, Throwable> next) {
		assert !isComplete();
		super.subscribe(next);
	}

	@Override
	public T getResult() {
		if (isResult()) {
			return result;
		}
		throw new IllegalStateException();
	}

	@Override
	public Throwable getException() {
		if (isException()) {
			return exception;
		}
		throw new IllegalStateException();
	}

	@Override
	public Try<T> getTry() {
		return isComplete() ? Try.of(result, exception) : null;
	}

	@Override
	public boolean setTo(BiConsumer<? super T, Throwable> consumer) {
		if (isComplete()) {
			consumer.accept(result, exception);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean setResultTo(Consumer<? super T> consumer) {
		if (isResult()) {
			consumer.accept(result);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean setExceptionTo(Consumer<Throwable> consumer) {
		if (isException()) {
			consumer.accept(exception);
			return true;
		} else {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	public <U> Stage<U> mold() {
		assert isException() : "Trying to mold a successful SettableStage!";
		return (Stage<U>) this;
	}

	@Override
	public <U, S extends BiConsumer<? super T, Throwable> & Stage<U>> Stage<U> then(S stage) {
		if (isComplete()) {
			stage.accept(result, exception);
			return stage;
		}
		return super.then(stage);
	}

	@Override
	public <U> Stage<U> thenApply(Function<? super T, ? extends U> fn) {
		if (isComplete()) return isResult() ? Stage.of(fn.apply(result)) : mold();
		return super.thenApply(fn);
	}

	@Override
	public <U> Stage<U> thenApplyEx(BiFunction<? super T, Throwable, ? extends U> fn) {
		if (isComplete()) return Stage.of(fn.apply(result, exception));
		return super.thenApplyEx(fn);
	}

	@Override
	public Stage<T> thenRun(Runnable action) {
		if (isComplete()) {
			if (isResult()) action.run();
			return this;
		}
		return super.thenRun(action);
	}

	@Override
	public Stage<T> thenRunEx(Runnable action) {
		if (isComplete()) {
			action.run();
			return this;
		}
		return super.thenRunEx(action);
	}

	@Override
	public <U> Stage<U> thenCompose(Function<? super T, ? extends Stage<U>> fn) {
		if (isComplete()) {
			return isResult() ? fn.apply(result) : mold();
		}
		return super.thenCompose(fn);
	}

	@Override
	public <U> Stage<U> thenComposeEx(BiFunction<? super T, Throwable, ? extends Stage<U>> fn) {
		if (isComplete()) {
			return fn.apply(result, exception);
		}
		return super.thenComposeEx(fn);
	}

	@Override
	public Stage<T> whenComplete(BiConsumer<? super T, Throwable> action) {
		if (isComplete()) {
			action.accept(result, exception);
			return this;
		}
		return super.whenComplete(action);
	}

	@Override
	public Stage<T> whenResult(Consumer<? super T> action) {
		if (isComplete()) {
			if (isResult()) action.accept(result);
			return this;
		}
		return super.whenResult(action);
	}

	@Override
	public Stage<T> whenException(Consumer<Throwable> action) {
		if (isComplete()) {
			if (isException()) action.accept(exception);
			return this;
		}
		return super.whenException(action);
	}

	@Override
	public Stage<T> thenException(Function<? super T, Throwable> fn) {
		if (isComplete()) {
			return isResult() ? Stage.ofException(fn.apply(result)) : mold();
		}
		return super.thenException(fn);
	}

	@Override
	public <U> Stage<U> thenTry(ThrowingFunction<? super T, ? extends U> fn) {
		if (isComplete()) {
			if (isException()) return mold();
			try {
				return Stage.of(fn.apply(result));
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				return Stage.ofException(e);
			}
		}
		return super.thenTry(fn);
	}

	@Override
	public MaterializedStage<T> async() {
		if (isComplete()) {
			SettableStage<T> result = new SettableStage<>();
			getCurrentEventloop().post(isResult() ?
					() -> result.set(this.result) :
					() -> result.setException(exception));
			return result;
		}
		return this;
	}

	@Override
	public Stage<Try<T>> toTry() {
		if (isComplete()) {
			return Stage.of(Try.of(result, exception));
		}
		return super.toTry();
	}

	@Override
	public Stage<Void> toVoid() {
		if (isComplete()) {
			return isResult() ? Stage.complete() : mold();
		}
		return super.toVoid();
	}

	@Override
	public <U, V> Stage<V> combine(Stage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
		if (isComplete()) {
			return Stage.of(result, exception).combine(other, fn);
		}
		return super.combine(other, fn);
	}

	@Override
	public Stage<Void> both(Stage<?> other) {
		if (isComplete()) {
			return Stage.of(result, exception).both(other);
		}
		return super.both(other);
	}

	@Override
	public Stage<T> either(Stage<? extends T> other) {
		if (isComplete()) {
			return Stage.of(result, exception).either(other);
		}
		return super.either(other);
	}

	@Override
	public Stage<T> timeout(@Nullable Duration timeout) {
		if (isComplete()) {
			return this;
		}
		return super.timeout(timeout);
	}

	@Override
	public CompletableFuture<T> toCompletableFuture() {
		if (isComplete()) {
			return Stage.of(result, exception).toCompletableFuture();
		}
		return super.toCompletableFuture();
	}

	@Override
	public String toString() {
		return "SettableStage{" + String.valueOf(isComplete() ?
				(exception == null ?
						"result=" + result :
						"exception=" + exception.getClass().getSimpleName()) :
				"<uncomplete>") + '}';
	}
}
