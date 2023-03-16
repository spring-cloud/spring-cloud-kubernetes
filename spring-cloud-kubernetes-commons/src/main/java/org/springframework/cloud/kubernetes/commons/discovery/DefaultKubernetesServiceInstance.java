/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.commons.discovery;

import java.net.URI;
import java.util.Map;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;

/**
 * @author wind57
 * @param instanceId the id of the instance.
 * @param serviceId the id of the service.
 * @param host the address where the service instance can be found.
 * @param port the port on which the service is running.
 * @param metadata a map containing metadata.
 * @param secure indicates whether the connection needs to be secure.
 * @param namespace the namespace of the service.
 * @param cluster the cluster the service resides in.
 */
public record DefaultKubernetesServiceInstance(String instanceId, String serviceId, String host, int port,
		Map<String, String> metadata, boolean secure, String namespace, String cluster,
		Map<String, Map<String, String>> podMetadata) implements KubernetesServiceInstance {

	/**
	 * @param instanceId the id of the instance.
	 * @param serviceId the id of the service.
	 * @param host the address where the service instance can be found.
	 * @param port the port on which the service is running.
	 * @param metadata a map containing metadata.
	 * @param secure indicates whether the connection needs to be secure.
	 */
	public DefaultKubernetesServiceInstance(String instanceId, String serviceId, String host, int port,
			Map<String, String> metadata, boolean secure) {
		this(instanceId, serviceId, host, port, metadata, secure, null, null, Map.of());
	}

	public DefaultKubernetesServiceInstance(String instanceId, String serviceId, String host, int port,
			Map<String, String> metadata, boolean secure, String namespace, String cluster) {
		this(instanceId, serviceId, host, port, metadata, secure, namespace, cluster, Map.of());
	}

	@Override
	public String getInstanceId() {
		return this.instanceId;
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
		return createUri(secure ? HTTPS : HTTP, host, port);
	}

	@Override
	public Map<String, String> getMetadata() {
		return metadata;
	}

	@Override
	public String getScheme() {
		return isSecure() ? HTTPS : HTTP;
	}

	@Override
	public String getNamespace() {
		return namespace != null ? namespace : this.metadata.get(NAMESPACE_METADATA_KEY);
	}

	@Override
	public String getCluster() {
		return this.cluster;
	}

	@Override
	public Map<String, Map<String, String>> podMetadata() {
		return podMetadata;
	}

	private URI createUri(String scheme, String host, int port) {
		// assume ExternalName type of service
		if (port == -1) {
			return URI.create(host);
		}

		// assume an endpoint without ports
		if (port == 0) {
			return URI.create(scheme + "://" + host);
		}
		return URI.create(scheme + "://" + host + ":" + port);
	}
}
