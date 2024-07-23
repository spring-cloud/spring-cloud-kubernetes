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
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

import static java.util.Optional.ofNullable;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NON_DETERMINISTIC_PORT_MESSAGE;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PORT_NAME_PROPERTY;
import static org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver.Input;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientServiceInstanceMapper implements KubernetesServiceInstanceMapper<V1Service> {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientServiceInstanceMapper.class));

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
		V1ObjectMeta metadata = service.getMetadata();

		List<V1ServicePort> ports = ofNullable(service.getSpec()).map(V1ServiceSpec::getPorts).orElse(List.of());
		V1ServicePort port;

		if (ports.isEmpty()) {
			LOG.warn(() -> "service : " + metadata.getName() + " does not have any ServicePort(s),"
					+ " will not consider it for load balancing");
			return null;
		}

		if (ports.size() == 1) {
			LOG.debug(() -> "single ServicePort found, will use it as-is " + "(without checking " + PORT_NAME_PROPERTY
					+ ")");
			port = ports.get(0);
		}
		else {
			String portNameFromProperties = properties.getPortName();
			if (StringUtils.hasText(portNameFromProperties)) {
				Optional<V1ServicePort> optionalPort = ports.stream()
					.filter(x -> Objects.equals(x.getName(), portNameFromProperties))
					.findAny();
				if (optionalPort.isPresent()) {
					LOG.debug(() -> "found port name that matches : " + portNameFromProperties);
					port = optionalPort.get();
				}
				else {
					logWarning(portNameFromProperties);
					port = ports.get(0);
				}
			}
			else {
				LOG.warn(() -> PORT_NAME_PROPERTY + " is not set");
				LOG.warn(() -> NON_DETERMINISTIC_PORT_MESSAGE);
				port = ports.get(0);
			}
		}

		String host = KubernetesServiceInstanceMapper.createHost(service.getMetadata().getName(),
				service.getMetadata().getNamespace(), properties.getClusterDomain());

		boolean secure = secure(port, service);

		return new DefaultKubernetesServiceInstance(metadata.getUid(), metadata.getName(), host, port.getPort(),
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

	private void logWarning(String portNameFromProperties) {
		LOG.warn(() -> "Did not find a port name that is equal to the value " + portNameFromProperties);
		LOG.warn(() -> NON_DETERMINISTIC_PORT_MESSAGE);
	}

}
