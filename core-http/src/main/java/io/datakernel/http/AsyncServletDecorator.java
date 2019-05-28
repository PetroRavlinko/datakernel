package io.datakernel.http;

import io.datakernel.async.Promise;
import io.datakernel.exception.UncheckedException;
import io.datakernel.util.MemSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.datakernel.csp.ChannelConsumers.recycling;

public interface AsyncServletDecorator {
	@NotNull AsyncServlet then(@NotNull AsyncServlet servlet);

	default @NotNull AsyncServlet map(@NotNull Function<HttpRequest, HttpResponse> fn) {
		return then(AsyncServlet.of(fn));
	}

	default @NotNull AsyncServlet mapBlocking(@NotNull BlockingServlet fn) {
		return then(AsyncServlet.ofBlocking(fn));
	}

	default @NotNull AsyncServlet mapBlocking(@Nullable Executor executor, @NotNull BlockingServlet fn) {
		return then(AsyncServlet.ofBlocking(executor, fn));
	}

	default AsyncServletDecorator wrap(AsyncServletDecorator next) {
		return servlet -> this.then(next.then(servlet));
	}

	static AsyncServletDecorator identity() {
		return servlet -> servlet;
	}

	@NotNull
	static AsyncServletDecorator of(AsyncServletDecorator... decorators) {
		return of(Arrays.asList(decorators));
	}

	@NotNull
	static AsyncServletDecorator of(List<AsyncServletDecorator> decorators) {
		return decorators.stream()
				.reduce(identity(), AsyncServletDecorator::wrap);
	}

	static AsyncServletDecorator onRequest(Consumer<HttpRequest> consumer) {
		return servlet ->
				request -> {
					consumer.accept(request);
					return servlet.serve(request);
				};
	}

	static AsyncServletDecorator onResponse(Consumer<HttpResponse> consumer) {
		return servlet ->
				request -> servlet.serve(request).whenResult(consumer);
	}

	static AsyncServletDecorator onResponse(BiConsumer<HttpRequest, HttpResponse> consumer) {
		return servlet ->
				request -> servlet.serve(request)
						.whenResult(response -> consumer.accept(request, response));
	}

	static AsyncServletDecorator mapResponse(Function<HttpResponse, HttpResponse> fn) {
		return servlet ->
				request -> servlet.serve(request)
						.map(response -> {
							HttpResponse newResponse = fn.apply(response);
							if (response != newResponse) {
								response.getBodyStream().streamTo(recycling());
								response.recycle();
							}
							return newResponse;
						});
	}

	static AsyncServletDecorator mapResponse(BiFunction<HttpRequest, HttpResponse, HttpResponse> fn) {
		return servlet ->
				request -> servlet.serve(request)
						.map(response -> {
							HttpResponse newResponse = fn.apply(request, response);
							if (response != newResponse) {
								response.getBodyStream().streamTo(recycling());
								response.recycle();
							}
							return newResponse;
						});
	}

	static AsyncServletDecorator onException(BiConsumer<HttpRequest, Throwable> consumer) {
		return servlet ->
				request -> servlet.serve(request).whenException((e -> consumer.accept(request, e)));
	}

	static AsyncServletDecorator mapException(Function<Throwable, HttpResponse> fn) {
		return servlet ->
				request -> servlet.serve(request)
						.thenEx(((response, e) -> {
							if (e == null) {
								return Promise.of(response);
							} else {
								return Promise.of(fn.apply(e));
							}
						}));
	}

	static AsyncServletDecorator mapException(BiFunction<HttpRequest, Throwable, HttpResponse> fn) {
		return servlet ->
				request -> servlet.serve(request)
						.thenEx(((response, e) -> {
							if (e == null) {
								return Promise.of(response);
							} else {
								return Promise.of(fn.apply(request, e));
							}
						}));
	}

	static AsyncServletDecorator catchUncheckedExceptions() {
		return servlet ->
				request -> {
					try {
						return servlet.serve(request);
					} catch (UncheckedException u) {
						return Promise.ofException(u.getCause());
					}
				};
	}

	static AsyncServletDecorator catchRuntimeExceptions() {
		return servlet ->
				request -> {
					try {
						return servlet.serve(request);
					} catch (UncheckedException u) {
						return Promise.ofException(u.getCause());
					} catch (RuntimeException e) {
						return Promise.ofException(e);
					}
				};
	}

	static AsyncServletDecorator setMaxBodySize(MemSize maxBodySize) {
		return setMaxBodySize(maxBodySize.toInt());
	}

	static AsyncServletDecorator setMaxBodySize(int maxBodySize) {
		return servlet ->
				request -> {
					request.setMaxBodySize(maxBodySize);
					return servlet.serve(request);
				};
	}

	static AsyncServletDecorator loadBody() {
		return servlet ->
				request -> request.loadBody()
						.then($ -> servlet.serve(request));
	}

	static AsyncServletDecorator loadBody(MemSize maxBodySize) {
		return servlet ->
				request -> request.loadBody(maxBodySize)
						.then($ -> servlet.serve(request));
	}

	static AsyncServletDecorator loadBody(int maxBodySize) {
		return servlet ->
				request -> request.loadBody(maxBodySize)
						.then($ -> servlet.serve(request));
	}

}
