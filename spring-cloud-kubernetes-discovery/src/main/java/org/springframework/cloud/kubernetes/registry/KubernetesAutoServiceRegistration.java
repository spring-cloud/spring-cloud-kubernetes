package org.springframework.cloud.kubernetes.discovery;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesServiceRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;

public class KubernetesAutoServiceRegistration implements AutoServiceRegistration,
														  SmartLifecycle,
														  Ordered {

	private static final Log log = LogFactory.getLog(KubernetesAutoServiceRegistration.class);

	private AtomicBoolean running = new AtomicBoolean(false);

	private int order = 0;

	private AtomicInteger port = new AtomicInteger(0);

	private ApplicationContext context;

	private KubernetesServiceRegistry serviceRegistry;

	private KubernetesRegistration registration;

	public KubernetesAutoServiceRegistration(ApplicationContext context,
											 KubernetesServiceRegistry serviceRegistry,
											 KubernetesRegistration registration) {
		this.context = context;
		this.serviceRegistry = serviceRegistry;
		this.registration = registration;
	}


	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		callback.run();

	}

	@Override
	public void start() {
		this.serviceRegistry.register(this.registration);

		this.context.publishEvent(
			new InstanceRegisteredEvent<>(this, this.registration.getInstanceConfig()));
		this.running.set(true);
	}

	@Override
	public void stop() {
		this.serviceRegistry.deregister(this.registration);
		this.running.set(false);
	}

	@Override
	public boolean isRunning() {
		return this.running.get();
	}

	@Override
	public int getPhase() {
		return 0;
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@EventListener(ServletWebServerInitializedEvent.class)
	public void onApplicationEvent(ServletWebServerInitializedEvent event) {
		// TODO: take SSL into account
		int localPort = event.getWebServer().getPort();
		if (this.port.get() == 0) {
			log.info("Updating port to " + localPort);
			this.port.compareAndSet(0, localPort);
			start();
		}
	}

	@EventListener(ContextClosedEvent.class)
	public void onApplicationEvent(ContextClosedEvent event) {
		if( event.getApplicationContext() == context ) {
			stop();
		}
	}
}
