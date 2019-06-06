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

import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.module.Module;
import io.datakernel.di.annotation.Provides;
import io.datakernel.dns.AsyncDnsClient;
import io.datakernel.dns.RemoteAsyncDnsClient;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.http.AsyncHttpClient;
import io.datakernel.http.HttpRequest;
import io.datakernel.launcher.Launcher;
import io.datakernel.logger.LoggerConfigurer;
import io.datakernel.service.ServiceGraphModule;

import static io.datakernel.bytebuf.ByteBufStrings.encodeAscii;
import static io.datakernel.config.ConfigConverters.ofDuration;
import static io.datakernel.config.ConfigConverters.ofInetAddress;
import static io.datakernel.di.module.Modules.combine;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * HTTP client example.
 * You can launch HttpServerExample to test this.
 */
public final class HttpClientExample extends Launcher {
	static {
		LoggerConfigurer.enableLogging();
	}
	@Inject
	AsyncHttpClient httpClient;

	@Inject
	Eventloop eventloop;

	@Inject
	Config config;

	private String addr;

	@Provides
	Eventloop eventloop() {
		return Eventloop.create();
	}

	@Provides
	AsyncHttpClient client(Eventloop eventloop, AsyncDnsClient dnsClient) {
		return AsyncHttpClient.create(eventloop)
				.withDnsClient(dnsClient);
	}

	@Provides
	AsyncDnsClient dnsClient(Eventloop eventloop, Config config) {
		return RemoteAsyncDnsClient.create(eventloop)
				.withDnsServerAddress(config.get(ofInetAddress(), "http.client.googlePublicDns"))
				.withTimeout(config.get(ofDuration(), "http.client.timeout"));
	}

	@Override
	protected Module getModule() {
		return combine(
				ServiceGraphModule.defaultInstance(),
				ConfigModule.create(Config.create()
						.with("http.client.googlePublicDns", "8.8.8.8")
						.with("http.client.timeout", "3 seconds")
						.with("http.client.host", "http://127.0.0.1:8080"))
		);
	}

	@Override
	protected void onStart() {
		addr = config.get("http.client.host");
	}

	@Override
	protected void run() {
		eventloop.post(() -> {
			String msg = "Hello from client!";

			HttpRequest request = HttpRequest.post(addr).withBody(encodeAscii(msg));

			httpClient.request(request)
					.whenResult(response -> response.loadBody()
							.whenComplete((body, e) -> {
								if (e == null) {
									body = response.getBody();
									System.out.println("Server response: " + body.asString(UTF_8));
								} else {
									System.err.println("Server error: " + e);
								}
							}));
		});
	}

	public static void main(String[] args) throws Exception {
		HttpClientExample example = new HttpClientExample();
		example.launch(args);
	}
}
