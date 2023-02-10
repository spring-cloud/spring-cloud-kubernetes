/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;

/**
 * @author wind57
 */
final class KubernetesDiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(
		LogFactory.getLog(KubernetesDiscoveryClientUtils.class));

	private KubernetesDiscoveryClientUtils() {

	}

	static EndpointSubsetNS subsetsFromEndpoints(Endpoints endpoints, Supplier<String> clientNamespace) {
		if (endpoints != null && endpoints.getSubsets() != null) {
			return new EndpointSubsetNS(endpoints.getMetadata().getNamespace(), endpoints.getSubsets());
		}
		return new EndpointSubsetNS(clientNamespace.get(), List.of());
	}

	static int endpointsPort(EndpointSubset endpointSubset, String serviceId, KubernetesDiscoveryProperties properties,
			Service service) {

		List<EndpointPort> endpointPorts = endpointSubset.getPorts();
		if (endpointPorts.size() == 1) {
			int port = endpointPorts.get(0).getPort();
			LOG.debug(() -> "endpoint ports has a single entry, will return port : " + port);
			return port;
		}

		else {
//			Map<String, Integer> ports = endpointPorts.stream().filter(p -> StringUtils.hasText(p.getName()))
//				.collect(Collectors.toMap(EndpointPort::getName, EndpointPort::getPort));
//			// This oneliner is looking for a port with a name equal to the primary port
//			// name specified in the service label
//			// or in spring.cloud.kubernetes.discovery.primary-port-name, equal to https,
//			// or equal to http.
//			// In case no port has been found return -1 to log a warning and fall back to
//			// the first port in the list.
//			int discoveredPort = ports.getOrDefault(primaryPortName,
//				ports.getOrDefault(HTTPS, ports.getOrDefault(HTTP, -1)));
//
//			if (discoveredPort == -1) {
//				if (StringUtils.hasText(primaryPortName)) {
//					log.warn("Could not find a port named '" + primaryPortName + "', 'https', or 'http' for service '"
//						+ serviceId + "'.");
//				}
//				else {
//					log.warn("Could not find a port named 'https' or 'http' for service '" + serviceId + "'.");
//				}
//				log.warn(
//					"Make sure that either the primary-port-name label has been added to the service, or that spring.cloud.kubernetes.discovery.primary-port-name has been configured.");
//				log.warn("Alternatively name the primary port 'https' or 'http'");
//				log.warn("An incorrect configuration may result in non-deterministic behaviour.");
//				discoveredPort = endpointPorts.get(0).getPort();
//			}
//			return discoveredPort;

			//TODO
			return 0;
		}
	}

	static String primaryPortName(KubernetesDiscoveryProperties properties, Service service, String serviceId) {
		String primaryPortNameFromProperties = properties.primaryPortName();
		Map<String, String> serviceLabels = service.getMetadata().getLabels();

		// the value from labels takes precedence over the one from properties
		String primaryPortName = Optional.ofNullable(
			Optional.ofNullable(serviceLabels).orElse(Map.of()).get(PRIMARY_PORT_NAME_LABEL_KEY)
		).orElse(primaryPortNameFromProperties);

		LOG.debug(() -> "will use primaryPortName : " + primaryPortName + " for service with ID = " + serviceId);
		return primaryPortName;
	}

}
