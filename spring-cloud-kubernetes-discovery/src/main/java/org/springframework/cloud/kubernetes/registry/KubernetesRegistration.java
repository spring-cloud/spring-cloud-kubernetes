package org.springframework.cloud.kubernetes.registry;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryProperties;

public class KubernetesRegistration implements Registration, Closeable {


	private final KubernetesClient client;
	private KubernetesDiscoveryProperties properties;
	private AtomicBoolean running = new AtomicBoolean(false);

	public KubernetesRegistration(KubernetesClient client,
								  KubernetesDiscoveryProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public void close() throws IOException {
		this.client.close();
	}

	@Override
	public String getServiceId() {
		return properties.getServiceName();
	}

	@Override
	public String getHost() {
		return client.getMasterUrl().getHost();
	}

	@Override
	public int getPort() {
		return 0;
	}

	@Override
	public boolean isSecure() {
		return false;
	}

	@Override
	public URI getUri() {
		try {
			return client.getMasterUrl().toURI();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

	public KubernetesDiscoveryProperties getProperties() {
		return properties;
	}

	@Override
	public Map<String, String> getMetadata() {
		return null;
	}

	@Override
	public String toString() {
		return "KubernetesRegistration{" +
			"client=" + client +
			", properties=" + properties +
			", running=" + running +
			'}';
	}
}
