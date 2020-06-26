/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.loadbalancer;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;

/**
 * @author Piotr Minkowski
 */
public class KubernetesServiceInstance implements ServiceInstance {

	private String serviceId;

	private String instanceId;

	private int port;

	private boolean secure;

	private String host;

	private URI uri;

	private Map<String, String> metadata;

	KubernetesServiceInstance(String serviceId, String instanceId, int port,
			boolean secure, String host, URI uri, Map<String, String> metadata) {
		this.serviceId = serviceId;
		this.instanceId = instanceId;
		this.port = port;
		this.secure = secure;
		this.host = host;
		this.uri = uri;
		this.metadata = metadata;
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

}
