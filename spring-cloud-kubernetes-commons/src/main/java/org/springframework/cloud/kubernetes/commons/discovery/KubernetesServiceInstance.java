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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import org.springframework.cloud.client.ServiceInstance;

public class KubernetesServiceInstance implements ServiceInstance {

	/**
	 * Key of the namespace metadata.
	 */
	public static final String NAMESPACE_METADATA_KEY = "k8s_namespace";

	private static final String HTTP_PREFIX = "http";

	private static final String HTTPS_PREFIX = "https";

	private static final String DSL = "//";

	private static final String COLON = ":";

	private String instanceId;

	private String serviceId;

	private String host;

	private int port;

	private URI uri;

	private Boolean secure;

	private Map<String, String> metadata;

	private String namespace;

	private String cluster;

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
		this.namespace = null;
		this.cluster = null;
	}

	/**
	 * @param instanceId the id of the instance.
	 * @param serviceId the id of the service.
	 * @param host the address where the service instance can be found.
	 * @param port the port on which the service is running.
	 * @param metadata a map containing metadata.
	 * @param secure indicates whether or not the connection needs to be secure.
	 * @param namespace the namespace of the service.
	 * @param cluster the clust the service resides in.
	 */
	public KubernetesServiceInstance(String instanceId, String serviceId, String host, int port,
			Map<String, String> metadata, Boolean secure, String namespace, String cluster) {
		this.instanceId = instanceId;
		this.serviceId = serviceId;
		this.host = host;
		this.port = port;
		this.metadata = metadata;
		this.secure = secure;
		this.uri = createUri(secure ? HTTPS_PREFIX : HTTP_PREFIX, host, port);
		this.namespace = namespace;
		this.cluster = cluster;
	}

	// Allows for deserialization
	public KubernetesServiceInstance() {
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
		return URI.create(scheme + COLON + DSL + host + COLON + port);
	}

	public String getNamespace() {
		return namespace != null ? namespace : this.metadata.get(NAMESPACE_METADATA_KEY);
	}

	public String getCluster() {
		return this.cluster;
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

	public void setUri(URI uri) {
		this.uri = uri;
	}

	public void setSecure(Boolean secure) {
		this.secure = secure;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void setCluster(String cluster) {
		this.cluster = cluster;
	}

	public Boolean getSecure() {
		return secure;
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
		return port == that.port && Objects.equals(instanceId, that.instanceId)
				&& Objects.equals(serviceId, that.serviceId) && Objects.equals(host, that.host)
				&& Objects.equals(uri, that.uri) && Objects.equals(secure, that.secure)
				&& Objects.equals(metadata, that.metadata) && Objects.equals(getNamespace(), that.getNamespace())
				&& Objects.equals(cluster, that.cluster);
	}

	@Override
	public String toString() {
		return "KubernetesServiceInstance{" + "instanceId='" + instanceId + '\'' + ", serviceId='" + serviceId + '\''
				+ ", host='" + host + '\'' + ", port=" + port + ", uri=" + uri + ", secure=" + secure + ", namespace="
				+ getNamespace() + ", cluster=" + cluster + ", metadata=" + metadata + '}';
	}

	@Override
	public int hashCode() {
		return Objects.hash(instanceId, serviceId, host, port, uri, secure, getNamespace(), cluster, metadata);
	}

}
