/*
 * Copyright 2013-2021 the original author or authors.
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
import java.util.Objects;

import org.springframework.cloud.client.ServiceInstance;

/**
 * @author Ryan Baxter
 */
public class KubernetesServiceInstance implements ServiceInstance {

	private String instanceId;

	private String serviceId;

	private String host;

	private int port;

	private boolean secure;

	private URI uri;

	private Map<String, String> metadata;

	private String scheme;

	private String namespace;

	public KubernetesServiceInstance() {
	}

	public KubernetesServiceInstance(String instanceId, String serviceId, String host, int port, boolean secure,
			URI uri, Map<String, String> metadata, String scheme, String namespace) {
		this.instanceId = instanceId;
		this.serviceId = serviceId;
		this.host = host;
		this.port = port;
		this.secure = secure;
		this.uri = uri;
		this.metadata = metadata;
		this.scheme = scheme;
		this.namespace = namespace;
	}

	@Override
	public String getInstanceId() {
		return instanceId;
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

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public void setUri(URI uri) {
		this.uri = uri;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	@Override
	public String getScheme() {
		return scheme;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		KubernetesServiceInstance that = (KubernetesServiceInstance) o;
		return getPort() == that.getPort() && isSecure() == that.isSecure()
				&& Objects.equals(getInstanceId(), that.getInstanceId())
				&& Objects.equals(getServiceId(), that.getServiceId()) && Objects.equals(getHost(), that.getHost())
				&& Objects.equals(getUri(), that.getUri()) && Objects.equals(getMetadata(), that.getMetadata())
				&& Objects.equals(getScheme(), that.getScheme()) && Objects.equals(getNamespace(), that.getNamespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getInstanceId(), getServiceId(), getHost(), getPort(), isSecure(), getUri(), getMetadata(),
				getScheme(), getNamespace());
	}

}
