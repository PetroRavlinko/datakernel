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

package io.datakernel.example;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.datakernel.async.ResultCallback;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigConverters;
import io.datakernel.eventloop.NioEventloop;
import io.datakernel.guice.boot.BootModule;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.http.HttpRequest;
import io.datakernel.http.HttpResponse;
import io.datakernel.http.server.AsyncHttpServlet;
import io.datakernel.launcher.Launcher;

import static io.datakernel.util.ByteBufStrings.encodeAscii;

public class SimpleHttpLauncherExample {
	public static void main(String[] args) throws Exception {
		Launcher.run(ServicesLauncher.class, args);
	}

	public static class ServicesLauncher extends Launcher {
		@Override
		protected void configure() {
			configs("launcher-example.properties");
			modules(BootModule.defaultBootModule(),
					new LauncherExampleModule());
		}

		@Override
		protected void doRun() throws Exception {
			awaitShutdown();
		}
	}

	public static class LauncherExampleModule extends AbstractModule {

		@Override
		protected void configure() {
		}

		@Provides
		@Singleton
		NioEventloop workerEventloop() {
			return new NioEventloop();
		}

		@Provides
		@Singleton
		AsyncHttpServer workerHttpServer(NioEventloop eventloop, final Config config) {
			AsyncHttpServer httpServer = new AsyncHttpServer(eventloop, new AsyncHttpServlet() {
				@Override
				public void serveAsync(HttpRequest request, ResultCallback<HttpResponse> callback) {
					final String responseMessage = ConfigConverters.ofString().get(config.getChild("responseMessage"));
					HttpResponse content = HttpResponse.create().body(ByteBuf.wrap(encodeAscii(
							"Message: " + responseMessage + "\n")));
					callback.onResult(content);
				}
			});
			int port = ConfigConverters.ofInteger().get(config.getChild("port"));
			return httpServer.setListenPort(port);
		}

	}
}
