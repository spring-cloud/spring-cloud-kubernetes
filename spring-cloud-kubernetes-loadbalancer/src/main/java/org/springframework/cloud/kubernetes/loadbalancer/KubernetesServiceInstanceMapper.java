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

package org.springframework.cloud.kubernetes.loadbalancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.utils.Utils;
import org.apache.commons.lang.StringUtils;

import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.discovery.KubernetesServiceInstance;

/**
 * Class for mapping Kubernetes Service object into {@link KubernetesServiceInstance}.
 *
 * @author Piotr Minkowski
 */
public class KubernetesServiceInstanceMapper {

	private final KubernetesLoadBalancerProperties properties;

	private final KubernetesDiscoveryProperties discoveryProperties;

	KubernetesServiceInstanceMapper(KubernetesLoadBalancerProperties properties,
			KubernetesDiscoveryProperties discoveryProperties) {
		this.properties = properties;
		this.discoveryProperties = discoveryProperties;
	}

	public KubernetesServiceInstance map(Service service) {
		final ObjectMeta meta = service.getMetadata();
		final List<ServicePort> ports = service.getSpec().getPorts();
		ServicePort port = null;
		if (ports.size() == 1) {
			port = ports.get(0);
		}
		else if (ports.size() > 1
				&& Utils.isNotNullOrEmpty(this.properties.getPortName())) {
			Optional<ServicePort> optPort = ports.stream()
					.filter(it -> properties.getPortName().endsWith(it.getName()))
					.findAny();
			if (optPort.isPresent()) {
				port = optPort.get();
			}
		}
		if (port == null) {
			return null;
		}
		final String host = createHost(service);
		final boolean secure = isSecure(service, port);
		return new KubernetesServiceInstance(meta.getUid(), meta.getName(), host,
				port.getPort(), getServiceMetadata(service), secure);
	}

	private Map<String, String> getServiceMetadata(Service service) {
		final Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = this.discoveryProperties
				.getMetadata();
		if (metadataProps.isAddLabels()) {
			Map<String, String> labelMetadata = getMapWithPrefixedKeys(
					service.getMetadata().getLabels(), metadataProps.getLabelsPrefix());
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.isAddAnnotations()) {
			Map<String, String> annotationMetadata = getMapWithPrefixedKeys(
					service.getMetadata().getAnnotations(),
					metadataProps.getAnnotationsPrefix());
			serviceMetadata.putAll(annotationMetadata);
		}

		return serviceMetadata;
	}

	private Map<String, String> getMapWithPrefixedKeys(Map<String, String> map,
			String prefix) {
		if (map == null) {
			return new HashMap<>();
		}
		if (!org.springframework.util.StringUtils.hasText(prefix)) {
			return map;
		}
		final Map<String, String> result = new HashMap<>();
		map.forEach((k, v) -> result.put(prefix + k, v));
		return result;
	}

	private boolean isSecure(Service service, ServicePort port) {
		final String securedLabelValue = service.getMetadata().getLabels()
				.getOrDefault("secured", "false");
		if (securedLabelValue.equals("true")) {
			return true;
		}
		final String securedAnnotationValue = service.getMetadata().getAnnotations()
				.getOrDefault("secured", "false");
		if (securedAnnotationValue.equals("true")) {
			return true;
		}
		return (port.getName() != null && port.getName().endsWith("https"))
				|| port.getPort().toString().endsWith("443");
	}

	private String createHost(Service service) {
		return String.format("%s.%s.svc.%s", service.getMetadata().getName(),
				StringUtils.isNotBlank(service.getMetadata().getNamespace())
						? service.getMetadata().getNamespace() : "default",
				properties.getClusterDomain());
	}

}
