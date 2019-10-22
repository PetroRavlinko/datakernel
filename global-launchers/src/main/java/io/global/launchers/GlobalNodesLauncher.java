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

package io.global.launchers;

import io.datakernel.async.EventloopTaskScheduler;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.annotation.Named;
import io.datakernel.di.annotation.Provides;
import io.datakernel.di.core.Key;
import io.datakernel.di.module.Module;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.jmx.JmxModule;
import io.datakernel.launcher.Launcher;
import io.datakernel.launcher.OnStart;
import io.datakernel.service.ServiceGraphModule;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static io.datakernel.config.Config.ofClassPathProperties;
import static io.datakernel.config.Config.ofProperties;
import static io.datakernel.di.module.Modules.combine;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class GlobalNodesLauncher extends Launcher {
	public static final String PROPERTIES_FILE = "global-nodes.properties";

	@Inject
	@Named("Nodes")
	AsyncHttpServer server;

	@Inject
	@Named("OT push")
	EventloopTaskScheduler otPushScheduler;

	@Inject
	@Named("FS push")
	EventloopTaskScheduler fsPushScheduler;

	@Inject
	@Named("FS catch up")
	EventloopTaskScheduler fsCatchUpScheduler;

	@Inject
	@Named("KV push")
	EventloopTaskScheduler kvPushScheduler;

	@Inject
	@Named("KV catch up")
	EventloopTaskScheduler kvCatchUpScheduler;

	@Inject
	@Named("PM push")
	EventloopTaskScheduler pmPushScheduler;

	@Inject
	@Named("PM catch up")
	EventloopTaskScheduler pmCatchUpScheduler;

	@Provides
	Config config() {
		return ofClassPathProperties(PROPERTIES_FILE)
				.overrideWith(ofProperties(System.getProperties()).getChild("config"));
	}

	@Provides
	Executor executor() {
		return newSingleThreadExecutor();
	}

	@Override
	protected final Module getModule() {
		return combine(
				ServiceGraphModule.create(),
				JmxModule.create(),
				ConfigModule.create()
						.printEffectiveConfig()
						.rebindImport(new Key<CompletionStage<Void>>() {}, new Key<CompletionStage<Void>>(OnStart.class) {}),
				new GlobalNodesModule());
	}

	@Override
	protected void run() throws Exception {
		awaitShutdown();
	}

	public static void main(String[] args) throws Exception {
		new GlobalNodesLauncher().launch(args);
	}
}
