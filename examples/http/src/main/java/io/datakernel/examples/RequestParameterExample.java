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

package io.datakernel.examples;

import io.datakernel.async.Promise;
import io.datakernel.di.annotation.Provides;
import io.datakernel.http.*;
import io.datakernel.launcher.Launcher;
import io.datakernel.launchers.http.HttpServerLauncher;
import io.datakernel.logger.LoggerConfigurer;

import static io.datakernel.bytebuf.ByteBufStrings.wrapUtf8;
import static io.datakernel.http.AsyncServletDecorator.loadBody;
import static io.datakernel.loader.StaticLoader.ofClassPath;

public final class RequestParameterExample extends HttpServerLauncher {
	private static final String RESOURCE_DIR = "static/query";
	static {
		LoggerConfigurer.enableLogging();
	}

	@Provides
	AsyncServlet servlet() {
		return RoutingServlet.create()
				.with(HttpMethod.POST, "/hello", loadBody()
						.serve(request -> {
							String name = request.getPostParameters().get("name");
							return Promise.of(HttpResponse.ok200()
									.withBody(wrapUtf8("<h1><center>Hello from POST, " + name + "!</center></h1>")));
						}))
				.with(HttpMethod.GET, "/hello", request -> {
					String name = request.getQueryParameter("name");
					return Promise.of(HttpResponse.ok200()
							.withBody(wrapUtf8("<h1><center>Hello from GET, " + name + "!</center></h1>")));
				})
						.with("/*", StaticServlet.create(ofClassPath(RESOURCE_DIR))
								.withIndexHtml());
	}

	public static void main(String[] args) throws Exception {
		Launcher launcher = new RequestParameterExample();
		launcher.launch(args);
	}
}
