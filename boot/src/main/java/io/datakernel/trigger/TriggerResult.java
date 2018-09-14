package io.datakernel.trigger;

import io.datakernel.annotation.Nullable;
import io.datakernel.jmx.ExceptionStats;
import io.datakernel.jmx.MBeanFormat;

import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.datakernel.jmx.MBeanFormat.formatExceptionMultiline;
import static io.datakernel.util.Preconditions.checkState;

public final class TriggerResult {
	private static final TriggerResult NONE = new TriggerResult(0, null, null);

	private final long timestamp;
	private final Throwable throwable;
	private final Object value;
	private final int count;

	TriggerResult(long timestamp, @Nullable Throwable throwable, @Nullable Object value, int count) {
		this.timestamp = timestamp;
		this.throwable = throwable;
		this.value = value;
		this.count = count;
	}

	TriggerResult(long timestamp, @Nullable Throwable throwable, @Nullable Object context) {
		this(timestamp, throwable, context, 1);
	}

	public static TriggerResult none() {
		return NONE;
	}

	public static TriggerResult create() {
		return new TriggerResult(0L, null, null);
	}

	public static TriggerResult create(long timestamp, Throwable throwable, int count) {
		return new TriggerResult(timestamp, throwable, null, count);
	}

	public static TriggerResult create(long timestamp, @Nullable Throwable throwable, @Nullable Object value) {
		return new TriggerResult(timestamp, throwable, value);
	}

	public static TriggerResult create(long timestamp, Throwable throwable, Object value, int count) {
		return new TriggerResult(timestamp, throwable, value, count);
	}

	public static TriggerResult create(Instant instant, Throwable throwable, Object value) {
		return create(instant.toEpochMilli(), throwable, value);
	}

	public static TriggerResult create(Instant instant, Throwable throwable, Object value, int count) {
		return create(instant.toEpochMilli(), throwable, value, count);
	}

	public static TriggerResult ofTimestamp(long timestamp) {
		return timestamp != 0L ?
				new TriggerResult(timestamp, null, null) : NONE;
	}

	public static TriggerResult ofTimestamp(long timestamp, boolean condition) {
		return timestamp != 0L && condition ?
				new TriggerResult(timestamp, null, null) : NONE;
	}

	public static TriggerResult ofInstant(@Nullable Instant instant) {
		return instant != null ?
				create(instant, null, null) : NONE;
	}

	public static TriggerResult ofInstant(Instant instant, boolean condition) {
		return instant != null && condition ?
				create(instant, null, null) : NONE;
	}

	public static TriggerResult ofError(Throwable throwable) {
		return throwable != null ?
				new TriggerResult(0L, throwable, null) : NONE;
	}

	public static TriggerResult ofError(Throwable throwable, long timestamp) {
		return throwable != null ?
				new TriggerResult(timestamp, throwable, null) : NONE;
	}

	public static TriggerResult ofError(Throwable throwable, Instant instant) {
		return throwable != null ?
				create(instant.toEpochMilli(), throwable, null) : NONE;
	}

	public static TriggerResult ofError(ExceptionStats exceptionStats) {
		Throwable lastException = exceptionStats.getLastException();
		return lastException != null ?
				create(exceptionStats.getLastTime() != null ?
								exceptionStats.getLastTime().toEpochMilli() :
								0,
						lastException, exceptionStats.getTotal()) :
				NONE;
	}

	public static TriggerResult ofValue(Object value) {
		return value != null ?
				new TriggerResult(0L, null, value) : NONE;
	}

	public static <T> TriggerResult ofValue(T value, Predicate<T> predicate) {
		return value != null && predicate.test(value) ?
				new TriggerResult(0L, null, value) : NONE;
	}

	public static <T> TriggerResult ofValue(T value, boolean condition) {
		return value != null && condition ?
				new TriggerResult(0L, null, value) : NONE;
	}

	public static <T> TriggerResult ofValue(Supplier<T> supplier, boolean condition) {
		return condition ? ofValue(supplier.get()) : NONE;
	}

	public TriggerResult withValue(Object value) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value) : NONE;
	}

	public TriggerResult withValue(Supplier<?> value) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value.get()) : NONE;
	}

	public TriggerResult withCount(int count) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value, count) : NONE;
	}

	public TriggerResult withCount(Supplier<Integer> count) {
		return isPresent() ? new TriggerResult(timestamp, throwable, value, count.get()) : NONE;
	}

	public TriggerResult when(boolean condition) {
		return isPresent() && condition ? this : NONE;
	}

	public TriggerResult when(Supplier<Boolean> conditionSupplier) {
		return isPresent() && conditionSupplier.get() ? this : NONE;
	}

	public TriggerResult whenTimestamp(Predicate<Long> timestampPredicate) {
		return isPresent() && hasTimestamp() && timestampPredicate.test(timestamp) ? this : NONE;
	}

	public TriggerResult whenInstant(Predicate<Instant> instantPredicate) {
		return isPresent() && hasTimestamp() && instantPredicate.test(Instant.ofEpochMilli(timestamp)) ? this : NONE;
	}

	@SuppressWarnings("unchecked")
	public <T> TriggerResult whenValue(Predicate<T> valuePredicate) {
		return isPresent() && hasValue() && valuePredicate.test((T) value) ? this : NONE;
	}

	public boolean isPresent() {
		return this != NONE;
	}

	public boolean hasTimestamp() {
		return timestamp != 0L;
	}

	public boolean hasThrowable() {
		return throwable != null;
	}

	public boolean hasValue() {
		return value != null;
	}

	public long getTimestamp() {
		checkState(isPresent());
		return timestamp;
	}

	@Nullable
	public Instant getInstant() {
		checkState(isPresent());
		return hasTimestamp() ? Instant.ofEpochMilli(timestamp) : null;
	}

	@Nullable
	public Throwable getThrowable() {
		checkState(isPresent());
		return throwable;
	}

	@Nullable
	public Object getValue() {
		checkState(isPresent());
		return value;
	}

	public int getCount() {
		checkState(isPresent());
		return count;
	}

	@Override
	public String toString() {
		return "@" + MBeanFormat.formatTimestamp(timestamp) +
				(count != 1 ? " #" + count : "") +
				(value != null ? " : " + value : "") +
				(throwable != null ? "\n" + formatExceptionMultiline(throwable) : "");
	}
}
