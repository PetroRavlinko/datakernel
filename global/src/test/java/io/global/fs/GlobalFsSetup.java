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

package io.global.fs;

import io.datakernel.async.Promises;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufQueue;
import io.datakernel.codec.StructuredCodec;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.exception.ParseException;
import io.datakernel.http.AsyncHttpClient;
import io.datakernel.remotefs.FsClient;
import io.datakernel.stream.processor.DatakernelRunner;
import io.datakernel.stream.processor.Manual;
import io.global.common.KeyPair;
import io.global.common.PrivKey;
import io.global.common.RawServerId;
import io.global.common.api.AnnounceData;
import io.global.common.api.DiscoveryService;
import io.global.common.discovery.HttpDiscoveryService;
import io.global.fs.api.GlobalFsNode;
import io.global.fs.http.HttpGlobalFsNode;
import io.global.fs.local.GlobalFsDriver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import static io.datakernel.eventloop.Eventloop.getCurrentEventloop;
import static io.datakernel.http.AsyncHttpClient.create;
import static io.datakernel.test.TestUtils.assertComplete;
import static io.datakernel.util.CollectionUtils.list;
import static io.datakernel.util.CollectionUtils.set;
import static io.global.common.BinaryDataFormats.REGISTRY;
import static io.global.common.SignedData.sign;
import static io.global.fs.api.CheckpointPosStrategy.fixed;
import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

@Manual("setting up methods that require running instances of servers")
@RunWith(DatakernelRunner.class)
public final class GlobalFsSetup {

	public static final StructuredCodec<AnnounceData> ANNOUNCE_DATA_CODEC = REGISTRY.get(AnnounceData.class);
	private KeyPair alice, bob;

	@Before
	public void setUp() throws ParseException {
		alice = PrivKey.fromString("d6577f45e352a16e21a29e8b9fb927b17902332c7f141e51a6265558c6bdd7ef").computeKeys();
		bob = PrivKey.fromString("538451a22387ba099222bdbfdeaed63435fde46c724eb3c72e8c64843c339ea1").computeKeys();
	}

	@Test
	public void uploadDownload() {
		AsyncHttpClient client = AsyncHttpClient.create(Eventloop.getCurrentEventloop());
		DiscoveryService discoveryService = HttpDiscoveryService.create(new InetSocketAddress(9001), client);

		String firstAddress = "http://127.0.0.1:8001/";
		String secondAddress = "http://127.0.0.1:8002/";
		RawServerId first = new RawServerId(firstAddress);
		RawServerId second = new RawServerId(secondAddress);

		GlobalFsNode firstClient = new HttpGlobalFsNode(client, firstAddress);
		GlobalFsNode secondClient = new HttpGlobalFsNode(client, secondAddress);

		GlobalFsDriver firstDriver = GlobalFsDriver.create(firstClient, discoveryService, list(alice), fixed(5));
		GlobalFsDriver secondDriver = GlobalFsDriver.create(secondClient, discoveryService, list(alice), fixed(6));

		FsClient firstAdapter = firstDriver.gatewayFor(alice.getPubKey());
		FsClient secondAdapter = secondDriver.gatewayFor(alice.getPubKey());

		String text1 = "Hello world, this is some bytes ";
		String text2 = "to be sent through the GlobalFs HTTP interface";

		ChannelSupplier<ByteBuf> supplier = ChannelSupplier.of(ByteBuf.wrapForReading(text1.getBytes(UTF_8)), ByteBuf.wrapForReading(text2.getBytes(UTF_8)));

		discoveryService.announce(alice.getPubKey(), sign(ANNOUNCE_DATA_CODEC, AnnounceData.of(123, set(first, second)), alice.getPrivKey()))
				.whenResult($ -> System.out.println("Servers announced"))
				.thenCompose($ -> firstAdapter.upload("test.txt"))
				.thenCompose(supplier::streamTo)
				.whenResult($ -> System.out.println("Upload to first server finished"))
				.thenCompose($ -> secondAdapter.download("test.txt"))
				.thenCompose(s -> s.toCollector(ByteBufQueue.collector()))
				.whenResult(s -> System.out.println("  downloaded: " + s.getString(UTF_8)))
				.whenResult(res -> assertEquals(text1 + text2, res.asString(UTF_8)))
				.whenResult($ -> System.out.println("Download from second server finished"))
				.whenComplete(assertComplete());
	}

	@Test
	public void announceNodes() {
		AsyncHttpClient client = create(getCurrentEventloop());
		DiscoveryService discoveryService = HttpDiscoveryService.create(new InetSocketAddress(9001), client);

		Set<RawServerId> servers = new HashSet<>();

		for (int i = 1; i <= parseInt(getProperty("globalfs.testing.numOfServers")); i++) {
			servers.add(new RawServerId("127.0.0.1:" + (8000 + i)));
		}

		Promises.all(
				discoveryService.announce(alice.getPubKey(), sign(ANNOUNCE_DATA_CODEC, AnnounceData.of(123, servers), alice.getPrivKey())),
				discoveryService.announce(bob.getPubKey(), sign(ANNOUNCE_DATA_CODEC, AnnounceData.of(234, servers), bob.getPrivKey()))
		)
				.whenComplete(assertComplete());
	}
}
