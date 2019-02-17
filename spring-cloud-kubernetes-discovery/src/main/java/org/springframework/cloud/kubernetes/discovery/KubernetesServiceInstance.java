/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;

import org.springframework.cloud.client.ServiceInstance;

/**
 * Kubernetes {@link ServiceInstance}.
 *
 * @author Ioannis Canellos
 */
public class KubernetesServiceInstance implements ServiceInstance {

	private static final String HTTP_PREFIX = "http://";

	private static final String HTTPS_PREFIX = "https://";

	private static final String COLN = ":";

	private final String instanceId;

	private final String serviceId;

	private final EndpointAddress endpointAddress;

	private final EndpointPort endpointPort;

	private final Boolean secure;

	private final Map<String, String> metadata;

	/**
	 * @param serviceId the id of the service.
	 * @param endpointAddress the address where the service instance can be found.
	 * @param endpointPort the port on which the service is running.
	 * @param metadata a map containing metadata.
	 * @param secure indicates whether or not the connection needs to be secure.
	 * @deprecated - use other constructor
	 */
	@Deprecated
	public KubernetesServiceInstance(String serviceId, EndpointAddress endpointAddress,
			EndpointPort endpointPort, Map<String, String> metadata, Boolean secure) {
		this(null, serviceId, endpointAddress, endpointPort, metadata, secure);
	}

	public KubernetesServiceInstance(String instanceId, String serviceId,
			EndpointAddress endpointAddress, EndpointPort endpointPort,
			Map<String, String> metadata, Boolean secure) {
		this.instanceId = instanceId;
		this.serviceId = serviceId;
		this.endpointAddress = endpointAddress;
		this.endpointPort = endpointPort;
		this.metadata = metadata;
		this.secure = secure;
	}

	@Override
	public String getInstanceId() {
		return this.instanceId;
	}

	@Override
	public String getServiceId() {
		return this.serviceId;
	}

	@Override
	public String getHost() {
		return this.endpointAddress.getIp();
	}

	@Override
	public int getPort() {
		return this.endpointPort.getPort();
	}

	@Override
	public boolean isSecure() {
		return this.secure;
	}

	@Override
	public URI getUri() {
		StringBuilder sb = new StringBuilder();
		sb.append(getScheme()).append(getHost()).append(COLN).append(getPort());
		try {
			return new URI(sb.toString());
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getMetadata() {
		return this.metadata;
	}

	@Override
	public String getScheme() {
		return isSecure() ? HTTPS_PREFIX : HTTP_PREFIX;
	}

}
