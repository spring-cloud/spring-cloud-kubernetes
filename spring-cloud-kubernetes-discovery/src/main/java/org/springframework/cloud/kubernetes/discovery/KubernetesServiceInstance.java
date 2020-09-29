/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;

/**
 * Kubernetes {@link ServiceInstance}.
 *
 * @author Ioannis Canellos
 */
public class KubernetesServiceInstance implements ServiceInstance {

	/**
	 * Key of the namespace metadata.
	 */
	public static final String NAMESPACE_METADATA_KEY = "k8s_namespace";

	private static final String HTTP_PREFIX = "http";

	private static final String HTTPS_PREFIX = "https";

	private static final String DSL = "//";

	private static final String COLON = ":";

	private final String instanceId;

	private final String serviceId;

	private final String host;

	private final int port;

	private final URI uri;

	private final Boolean secure;

	private final Map<String, String> metadata;

	/**
	 * @param instanceId the id of the instance.
	 * @param serviceId the id of the service.
	 * @param host the address where the service instance can be found.
	 * @param port the port on which the service is running.
	 * @param metadata a map containing metadata.
	 * @param secure indicates whether or not the connection needs to be secure.
	 */
	public KubernetesServiceInstance(String instanceId, String serviceId, String host, int port,
			Map<String, String> metadata, Boolean secure) {
		this.instanceId = instanceId;
		this.serviceId = serviceId;
		this.host = host;
		this.port = port;
		this.metadata = metadata;
		this.secure = secure;
		this.uri = createUri(secure ? HTTPS_PREFIX : HTTP_PREFIX, host, port);
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
		return this.host;
	}

	@Override
	public int getPort() {
		return this.port;
	}

	@Override
	public boolean isSecure() {
		return this.secure;
	}

	@Override
	public URI getUri() {
		return uri;
	}

	public Map<String, String> getMetadata() {
		return this.metadata;
	}

	@Override
	public String getScheme() {
		return isSecure() ? HTTPS_PREFIX : HTTP_PREFIX;
	}

	private URI createUri(String scheme, String host, int port) {
		StringBuilder sb = new StringBuilder();
		sb.append(scheme).append(COLON).append(DSL).append(host).append(COLON).append(port);
		return URI.create(sb.toString());
	}

	public String getNamespace() {
		return this.metadata != null ? this.metadata.get(NAMESPACE_METADATA_KEY) : null;
	}

}
