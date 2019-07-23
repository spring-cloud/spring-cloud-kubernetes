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

import java.util.ArrayList;
import java.util.List;

import com.netflix.loadbalancer.Server;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Utils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * the KubernetesEndpointsServerList description.
 *
 * @author wuzishu
 */
public class KubernetesEndpointsServerList extends KubernetesServerList {

	private static final Log LOG = LogFactory.getLog(KubernetesEndpointsServerList.class);

	/**
	 * Instantiates a new Kubernetes endpoints server list.
	 * @param client the client
	 * @param properties the properties
	 */
	KubernetesEndpointsServerList(KubernetesClient client,
			KubernetesRibbonProperties properties) {
		super(client, properties);
	}

	@Override
	public List<Server> getUpdatedListOfServers() {
		List<Server> result = new ArrayList<>();
		Endpoints endpoints = StringUtils.isNotBlank(this.getNamespace())
				? this.getClient().endpoints().inNamespace(this.getNamespace())
						.withName(this.getServiceId()).get()
				: this.getClient().endpoints().withName(this.getServiceId()).get();
		if (endpoints != null) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format(
						"Found [%d] endpoints in l [%s] for name [%s] and portName [%s]",
						endpoints.getSubsets().size(),
						endpoints.getMetadata().getNamespace(), this.getServiceId(),
						this.getPortName()));
			}
			for (EndpointSubset subset : endpoints.getSubsets()) {

				if (subset.getPorts().size() == 1) {
					EndpointPort port = subset.getPorts().get(getFIRST());
					for (EndpointAddress address : subset.getAddresses()) {
						result.add(new Server(address.getIp(), port.getPort()));
					}
				}
				else {
					for (EndpointPort port : subset.getPorts()) {
						if (Utils.isNullOrEmpty(this.getPortName())
								|| this.getPortName().endsWith(port.getName())) {
							for (EndpointAddress address : subset.getAddresses()) {
								result.add(new Server(address.getIp(), port.getPort()));
							}
						}
					}
				}
			}
		}
		if (result.isEmpty()) {
			LOG.warn(String.format(
					"Did not find any endpoints in ribbon in namespace [%s] for name [%s] and portName [%s]",
					this.getNamespace(), this.getServiceId(), this.getPortName()));
		}

		return result;
	}

}
