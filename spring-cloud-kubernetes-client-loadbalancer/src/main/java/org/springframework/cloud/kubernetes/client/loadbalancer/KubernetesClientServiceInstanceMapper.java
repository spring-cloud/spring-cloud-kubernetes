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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientServiceInstanceMapper implements KubernetesServiceInstanceMapper<V1Service> {

	private KubernetesLoadBalancerProperties properties;

	private KubernetesDiscoveryProperties discoveryProperties;

	public KubernetesClientServiceInstanceMapper(KubernetesLoadBalancerProperties properties,
			KubernetesDiscoveryProperties discoveryProperties) {
		this.properties = properties;
		this.discoveryProperties = discoveryProperties;
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
		final String host = KubernetesServiceInstanceMapper.createHost(service.getMetadata().getName(),
				service.getMetadata().getNamespace(), properties.getClusterDomain());
		final boolean secure = KubernetesServiceInstanceMapper.isSecure(service.getMetadata().getLabels(),
				service.getMetadata().getAnnotations(), port.getName(), port.getPort());
		return new DefaultKubernetesServiceInstance(meta.getUid(), meta.getName(), host, port.getPort(),
				getServiceMetadata(service), secure);
	}

	private Map<String, String> getServiceMetadata(V1Service service) {
		final Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = this.discoveryProperties.metadata();
		if (metadataProps.addLabels()) {
			Map<String, String> labelMetadata = KubernetesServiceInstanceMapper
					.getMapWithPrefixedKeys(service.getMetadata().getLabels(), metadataProps.labelsPrefix());
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.addAnnotations()) {
			Map<String, String> annotationMetadata = KubernetesServiceInstanceMapper
					.getMapWithPrefixedKeys(service.getMetadata().getAnnotations(), metadataProps.annotationsPrefix());
			serviceMetadata.putAll(annotationMetadata);
		}

		return serviceMetadata;
	}

}
