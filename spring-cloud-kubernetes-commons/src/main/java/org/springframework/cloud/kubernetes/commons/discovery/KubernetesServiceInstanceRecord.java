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

import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.util.Map;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;

/**
 * @author wind57
 *
 * @param instanceId the id of the instance.
 * @param serviceId the id of the service.
 * @param host the address where the service instance can be found.
 * @param port the port on which the service is running.
 * @param metadata a map containing metadata.
 * @param secure indicates whether the connection needs to be secure.
 * @param namespace the namespace of the service.
 * @param cluster the cluster the service resides in.
 */
public record KubernetesServiceInstanceRecord(String instanceId, String serviceId, String host, int port,
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
		this(instanceId, serviceId, host, port, secure, metadata, null, null);
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
