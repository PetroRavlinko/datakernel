package io.datakernel.http.boot;

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigsModule;
import io.datakernel.config.impl.PropertiesConfig;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.http.AsyncServlet;
import io.datakernel.jmx.JmxModule;
import io.datakernel.launcher.Launcher;
import io.datakernel.launcher.modules.EventloopModule;
import io.datakernel.service.ServiceGraphModule;
import io.datakernel.trigger.TriggerRegistry;
import io.datakernel.trigger.TriggersModule;
import io.datakernel.util.guice.SimpleModule;

import java.net.InetSocketAddress;
import java.util.Collection;

import static com.google.inject.util.Modules.combine;
import static com.google.inject.util.Modules.override;
import static io.datakernel.config.ConfigConverters.ofInetSocketAddress;
import static io.datakernel.http.boot.ConfigUtils.initializeHttpServer;
import static io.datakernel.http.boot.ConfigUtils.initializeHttpServerTriggers;
import static java.lang.Boolean.parseBoolean;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public abstract class HttpServerLauncher extends Launcher {
	public static final String PRODUCTION_MODE = "production";
	public static final String PROPERTIES_FILE = "http-server.properties";
	public static final String PROPERTIES_FILE_EFFECTIVE = "http-server.effective.properties";
	public static final String BUSINESS_MODULE_PROP = "businessLogicModule";

	@Inject
	AsyncHttpServer httpServer;

	@Override
	protected final Collection<Module> getModules() {
		return asList(
				override(getBaseModules()).with(getOverrideModules()),
				combine(getBusinessLogicModules()));
	}

	private Collection<Module> getBaseModules() {
		return asList(
				ServiceGraphModule.defaultInstance(),
				JmxModule.create(),
				TriggersModule.create(),
				ConfigsModule.create(
						Config.create()
								.with("http.listenAddresses", Config.ofValue(ofInetSocketAddress(), new InetSocketAddress(8080)))
								.override(PropertiesConfig.ofProperties(PROPERTIES_FILE, true))
								.override(PropertiesConfig.ofProperties(System.getProperties()).getChild("config")))
						.saveEffectiveConfigTo(PROPERTIES_FILE_EFFECTIVE),
				EventloopModule.create(),
				new SimpleModule() {
					@Provides
					@Singleton
					public AsyncHttpServer provide(Eventloop eventloop, AsyncServlet rootServlet, TriggerRegistry triggerRegistry, Config config) {
						return AsyncHttpServer.create(eventloop, rootServlet)
								.initialize(server -> initializeHttpServer(server, config.getChild("http")))
								.initialize(server -> initializeHttpServerTriggers(server, triggerRegistry, config.getChild("triggers.http")));
					}
				}
		);
	}

	protected Collection<Module> getOverrideModules() {
		return emptyList();
	}

	protected abstract Collection<Module> getBusinessLogicModules();

	@Override
	protected void run() throws Exception {
		awaitShutdown();
	}

	public static void main(String[] args) throws Exception {
		String businessLogicModuleName = System.getProperty(BUSINESS_MODULE_PROP);
		Module businessLogicModule = businessLogicModuleName != null ?
				(Module) Class.forName(businessLogicModuleName).newInstance() :
				HelloWorldServletModule.create();

		Launcher launcher = new HttpServerLauncher() {
			@Override
			protected Collection<Module> getBusinessLogicModules() {
				return singletonList(businessLogicModule);
			}
		};
		launcher.launch(parseBoolean(System.getProperty(PRODUCTION_MODE)), args);
	}
}
