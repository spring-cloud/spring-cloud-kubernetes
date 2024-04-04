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

package org.springframework.cloud.kubernetes.client.loadbalancer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver.Input;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientServiceInstanceMapper implements KubernetesServiceInstanceMapper<V1Service> {

	/**
	 * empty on purpose, load balancer implementation does not need them.
	 */
	private static final Map<String, Integer> PORTS_DATA = Map.of();

	private final KubernetesLoadBalancerProperties properties;

	private final KubernetesDiscoveryProperties discoveryProperties;

	private final ServicePortSecureResolver resolver;

	public KubernetesClientServiceInstanceMapper(KubernetesLoadBalancerProperties properties,
			KubernetesDiscoveryProperties discoveryProperties) {
		this.properties = properties;
		this.discoveryProperties = discoveryProperties;
		resolver = new ServicePortSecureResolver(discoveryProperties);
	}

	@Override
	public KubernetesServiceInstance map(V1Service service) {
		final V1ObjectMeta meta = service.getMetadata();

		final List<V1ServicePort> ports = service.getSpec().getPorts();
		V1ServicePort port = null;
		if (ports.size() == 1) {
			port = ports.get(0);
		}
		else if (ports.size() > 1 && StringUtils.hasText(this.properties.getPortName())) {
			Optional<V1ServicePort> optPort = ports.stream()
					.filter(it -> properties.getPortName().endsWith(it.getName())).findAny();
			if (optPort.isPresent()) {
				port = optPort.get();
			}
		}
		if (port == null) {
			return null;
		}
		String host = KubernetesServiceInstanceMapper.createHost(service.getMetadata().getName(),
				service.getMetadata().getNamespace(), properties.getClusterDomain());

		boolean secure = secure(port, service);

		return new DefaultKubernetesServiceInstance(meta.getUid(), meta.getName(), host, port.getPort(),
				serviceMetadata(service), secure);
	}

	private Map<String, String> serviceMetadata(V1Service service) {
		V1ObjectMeta metadata = service.getMetadata();
		V1ServiceSpec serviceSpec = service.getSpec();
		ServiceMetadata serviceMetadata = new ServiceMetadata(metadata.getName(), metadata.getNamespace(),
				serviceSpec.getType(), metadata.getLabels(), metadata.getAnnotations());
		return DiscoveryClientUtils.serviceInstanceMetadata(PORTS_DATA, serviceMetadata, discoveryProperties);
	}

	private boolean secure(V1ServicePort port, V1Service service) {
		V1ObjectMeta metadata = service.getMetadata();
		ServicePortNameAndNumber portNameAndNumber = new ServicePortNameAndNumber(port.getPort(), port.getName());
		Input input = new Input(portNameAndNumber, metadata.getName(), metadata.getLabels(), metadata.getAnnotations());
		return resolver.resolve(input);
	}

}
