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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.utils.Utils;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;

/**
 * Class for mapping Kubernetes Service object into {@link KubernetesServiceInstance}.
 *
 * @author Piotr Minkowski
 */
public class Fabric8ServiceInstanceMapper implements KubernetesServiceInstanceMapper<Service> {

	/**
	 * empty on purpose, load balancer implementation does not need them.
	 */
	private static final Map<String, Integer> PORTS_DATA = Map.of();

	private final KubernetesLoadBalancerProperties properties;

	private final KubernetesDiscoveryProperties discoveryProperties;

	Fabric8ServiceInstanceMapper(KubernetesLoadBalancerProperties properties,
			KubernetesDiscoveryProperties discoveryProperties) {
		this.properties = properties;
		this.discoveryProperties = discoveryProperties;
	}

	@Override
	public KubernetesServiceInstance map(Service service) {
		ObjectMeta meta = service.getMetadata();
		List<ServicePort> ports = service.getSpec().getPorts();
		ServicePort port = null;
		if (ports.size() == 1) {
			port = ports.get(0);
		}
		else if (ports.size() > 1 && Utils.isNotNullOrEmpty(properties.getPortName())) {
			Optional<ServicePort> optPort = ports.stream().filter(it -> properties.getPortName().endsWith(it.getName()))
					.findAny();
			if (optPort.isPresent()) {
				port = optPort.get();
			}
		}
		if (port == null) {
			return null;
		}
		String host = KubernetesServiceInstanceMapper.createHost(service.getMetadata().getName(),
				service.getMetadata().getNamespace(), properties.getClusterDomain());
		boolean secure = KubernetesServiceInstanceMapper.isSecure(service.getMetadata().getLabels(),
				service.getMetadata().getAnnotations(), port.getName(), port.getPort());
		return new DefaultKubernetesServiceInstance(meta.getUid(), meta.getName(), host, port.getPort(),
				serviceMetadata(service), secure);
	}

	Map<String, String> serviceMetadata(Service service) {
		ServiceMetadata serviceMetadata = Fabric8Utils.serviceMetadata(service);
		return DiscoveryClientUtils.serviceInstanceMetadata(PORTS_DATA, serviceMetadata, discoveryProperties);
	}

}
