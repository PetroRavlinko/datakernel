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

package io.global.common.discovery;

import io.datakernel.async.Promise;
import io.datakernel.codec.StructuredCodec;
import io.datakernel.http.*;
import io.datakernel.util.TypeT;
import io.global.common.Hash;
import io.global.common.PubKey;
import io.global.common.SharedSimKey;
import io.global.common.SignedData;
import io.global.common.api.AnnounceData;
import io.global.common.api.DiscoveryService;

import java.util.List;

import static io.datakernel.codec.binary.BinaryUtils.decode;
import static io.datakernel.codec.binary.BinaryUtils.encode;
import static io.datakernel.http.AsyncServlet.ensureRequestBody;
import static io.global.common.BinaryDataFormats.REGISTRY;

public final class DiscoveryServlet implements WithMiddleware {
	public static final String ANNOUNCE_ALL = "announceAll";
	public static final String ANNOUNCE = "announce";
	public static final String FIND = "find";
	public static final String FIND_ALL = "findAll";
	public static final String SHARE_KEY = "shareKey";
	public static final String GET_SHARED_KEY = "getSharedKey";
	public static final String GET_SHARED_KEYS = "getSharedKeys";

	private final MiddlewareServlet servlet;

	static final StructuredCodec<SignedData<AnnounceData>> SIGNED_ANNOUNCE = REGISTRY.get(new TypeT<SignedData<AnnounceData>>() {});
	static final StructuredCodec<SignedData<SharedSimKey>> SIGNED_SHARED_SIM_KEY = REGISTRY.get(new TypeT<SignedData<SharedSimKey>>() {});
	static final StructuredCodec<List<SignedData<SharedSimKey>>> LIST_OF_SIGNED_SHARED_SIM_KEYS = REGISTRY.get(new TypeT<List<SignedData<SharedSimKey>>>() {});

	private DiscoveryServlet(DiscoveryService discoveryService) {
		this.servlet = servlet(discoveryService);
	}

	public static DiscoveryServlet create(DiscoveryService discoveryService) {
		return new DiscoveryServlet(discoveryService);
	}

	private MiddlewareServlet servlet(DiscoveryService discoveryService) {
		return MiddlewareServlet.create()
				.with(HttpMethod.PUT, "/" + ANNOUNCE_ALL + "/:owner", ensureRequestBody(request -> {
					PubKey owner = PubKey.fromString(request.getPathParameter("owner"));
					SignedData<AnnounceData> announceData = decode(SIGNED_ANNOUNCE, request.takeBody());
					return discoveryService.announce(owner, announceData)
							.thenApply($ -> HttpResponse.ok201());
				}))
				.with(HttpMethod.GET, "/" + FIND_ALL + "/:owner", request ->
						discoveryService.find(PubKey.fromString(request.getPathParameter("owner")))
								.thenComposeEx((data, e) ->
										e == null ?
												Promise.of(HttpResponse.ok200()
														.withBody(encode(SIGNED_ANNOUNCE, data))) :
												Promise.ofException(HttpException.notFound404())))
				.with(HttpMethod.POST, "/" + SHARE_KEY + "/:receiver", ensureRequestBody(request -> {
					PubKey receiver = PubKey.fromString(request.getPathParameter("receiver"));
					SignedData<SharedSimKey> simKey = decode(SIGNED_SHARED_SIM_KEY, request.takeBody());
					return discoveryService.shareKey(receiver, simKey)
							.thenApply($ -> HttpResponse.ok201());
				}))
				.with(HttpMethod.GET, "/" + GET_SHARED_KEY + "/:receiver/:hash", request -> {
					PubKey receiver = PubKey.fromString(request.getPathParameter("receiver"));
					Hash simKeyHash = Hash.parseString(request.getPathParameter("hash"));
					return discoveryService.getSharedKey(receiver, simKeyHash)
							.thenComposeEx((signedSharedKey, e) ->
									e == null ?
											Promise.of(HttpResponse.ok200()
													.withBody(encode(SIGNED_SHARED_SIM_KEY, signedSharedKey))) :
											Promise.ofException(HttpException.notFound404()));
				})
				.with(HttpMethod.GET, "/" + GET_SHARED_KEYS + "/:receiver", request ->
						discoveryService.getSharedKeys(PubKey.fromString(request.getPathParameter("receiver")))
								.thenApply(signedSharedKeys ->
										HttpResponse.ok200()
												.withBody(encode(LIST_OF_SIGNED_SHARED_SIM_KEYS, signedSharedKeys))));
	}

	@Override
	public MiddlewareServlet getMiddlewareServlet() {
		return servlet;
	}
}
