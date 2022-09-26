package org.springframework.cloud.kubernetes.commons.discovery;

import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.Map;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;

public record KubernetesServiceInstanceRecord(String instanceId, String serviceId, String host, int port, URI uri,
		boolean secure, Map<String, String> metadata, String namespace, String cluster) implements ServiceInstance {

	/**
	 * @param instanceId the id of the instance.
	 * @param serviceId the id of the service.
	 * @param host the address where the service instance can be found.
	 * @param port the port on which the service is running.
	 * @param metadata a map containing metadata.
	 * @param secure indicates whether the connection needs to be secure.
	 */
	public KubernetesServiceInstanceRecord(String instanceId, String serviceId, String host, int port,
			Map<String, String> metadata, boolean secure) {
		this(instanceId, serviceId, host, port, createUri(secure ? HTTPS : HTTP, host, port),
			secure, metadata, null, null);
	}

	@Override
	public String getServiceId() {
		return serviceId;
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public boolean isSecure() {
		return secure;
	}

	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public Map<String, String> getMetadata() {
		return metadata;
	}

	public String namespace() {
		return namespace != null ? namespace : this.metadata.get(NAMESPACE_METADATA_KEY);
	}

	private URI createUri(String scheme, String host, int port) {
		return URI.create(scheme + "://" + host + ":" + port);
	}
}
