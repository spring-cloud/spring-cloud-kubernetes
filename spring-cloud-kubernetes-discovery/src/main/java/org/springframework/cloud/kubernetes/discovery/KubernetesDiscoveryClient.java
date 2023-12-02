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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Ryan Baxter
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private final RestTemplate rest;

	private final boolean emptyNamespaces;

	private final Set<String> namespaces;

	private final String discoveryServerUrl;

	@Deprecated(forRemoval = true)
	public KubernetesDiscoveryClient(RestTemplate rest, KubernetesDiscoveryClientProperties properties) {
		if (!StringUtils.hasText(properties.getDiscoveryServerUrl())) {
			throw new DiscoveryServerUrlInvalidException();
		}
		this.rest = rest;
		this.emptyNamespaces = properties.getNamespaces().isEmpty();
		this.namespaces = properties.getNamespaces();
		this.discoveryServerUrl = properties.getDiscoveryServerUrl();
	}

	KubernetesDiscoveryClient(RestTemplate rest, KubernetesDiscoveryProperties kubernetesDiscoveryProperties) {
		if (!StringUtils.hasText(kubernetesDiscoveryProperties.discoveryServerUrl())) {
			throw new DiscoveryServerUrlInvalidException();
		}
		this.rest = rest;
		this.emptyNamespaces = kubernetesDiscoveryProperties.namespaces().isEmpty();
		this.namespaces = kubernetesDiscoveryProperties.namespaces();
		this.discoveryServerUrl = kubernetesDiscoveryProperties.discoveryServerUrl();
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		KubernetesServiceInstance[] responseBody = rest
				.getForEntity(discoveryServerUrl + "/apps/" + serviceId, KubernetesServiceInstance[].class).getBody();
		if (responseBody != null && responseBody.length > 0) {
			return Arrays.stream(responseBody).filter(this::matchNamespaces).collect(Collectors.toList());
		}
		return List.of();
	}

	@Override
	public List<String> getServices() {
		Service[] services = rest.getForEntity(discoveryServerUrl + "/apps", Service[].class).getBody();
		if (services != null && services.length > 0) {
			return Arrays.stream(services).filter(this::matchNamespaces).map(Service::getName).toList();
		}
		return List.of();
	}

	private boolean matchNamespaces(KubernetesServiceInstance kubernetesServiceInstance) {
		return emptyNamespaces || namespaces.contains(kubernetesServiceInstance.getNamespace());
	}

	private boolean matchNamespaces(Service service) {
		return service.getServiceInstances().isEmpty()
				|| service.getServiceInstances().stream().anyMatch(this::matchNamespaces);
	}

}
