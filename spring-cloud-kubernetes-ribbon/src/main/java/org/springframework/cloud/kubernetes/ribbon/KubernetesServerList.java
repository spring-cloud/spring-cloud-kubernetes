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

package org.springframework.cloud.kubernetes.ribbon;

import java.util.Collections;
import java.util.List;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Kubernetes {@link ServerList}.
 *
 * @author Ioannis Canellos
 * @author wuzishu
 */
public abstract class KubernetesServerList extends AbstractServerList<Server>
		implements ServerList<Server> {

	private static final int FIRST = 0;

	private final KubernetesClient client;

	private String serviceId;

	private String namespace;

	private String portName;

	private KubernetesRibbonProperties properties;

	/**
	 * Instantiates a new Kubernetes server list.
	 * @param client the client
	 * @param properties the properties
	 */
	public KubernetesServerList(KubernetesClient client,
			KubernetesRibbonProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	public void initWithNiwsConfig(IClientConfig clientConfig) {
		this.serviceId = clientConfig.getClientName();
		this.namespace = clientConfig.getPropertyAsString(KubernetesConfigKey.Namespace,
				this.client.getNamespace());
		this.portName = clientConfig.getPropertyAsString(KubernetesConfigKey.PortName,
				null);
	}

	public List<Server> getInitialListOfServers() {
		return Collections.emptyList();
	}

	/**
	 * Gets first.
	 * @return the first
	 */
	static int getFIRST() {
		return FIRST;
	}

	/**
	 * Gets client.
	 * @return the client
	 */
	KubernetesClient getClient() {
		return client;
	}

	/**
	 * Gets service id.
	 * @return the service id
	 */
	String getServiceId() {
		return serviceId;
	}

	/**
	 * Gets namespace.
	 * @return the namespace
	 */
	String getNamespace() {
		return namespace;
	}

	/**
	 * Gets port name.
	 * @return the port name
	 */
	String getPortName() {
		return portName;
	}

	/**
	 * Gets properties.
	 * @return the properties
	 */
	KubernetesRibbonProperties getProperties() {
		return properties;
	}

}
