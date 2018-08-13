/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class KubernetesServiceInstance implements ServiceInstance {

	private final URI uri;
	private final String host;
	private final String serviceId;

	private final int port;
	private final boolean secure;
	private final Map<String, String> metadata;

	public KubernetesServiceInstance(String serviceId,
									 EndpointAddress endpointAddress,
									 EndpointPort endpointPort,
									 Map<String, String> metadata,
									 boolean secure) {
		this.serviceId = serviceId;
		this.metadata = metadata;
		this.secure = secure;

		this.host = endpointAddress.getIp();
		this.port = endpointPort.getPort();
		this.uri = createUri(secure, host, port);
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

	private URI createUri(boolean secure, String host, int port) {
		StringBuilder sb = new StringBuilder();
		if (secure) {
			sb.append("https://");
		} else {
			sb.append("http://");
		}

		sb.append(host).append(":").append(port);
		try {
			return new URI(sb.toString());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}
}
