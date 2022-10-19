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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Ryan Baxter
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private RestTemplate rest;

	private KubernetesDiscoveryClientProperties properties;

	public KubernetesDiscoveryClient(RestTemplate rest, KubernetesDiscoveryClientProperties properties) {
		if (!StringUtils.hasText(properties.getDiscoveryServerUrl())) {
			throw new DiscoveryServerUrlInvalidException();
		}
		this.rest = rest;
		this.properties = properties;
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		List<ServiceInstance> response = Collections.emptyList();
		KubernetesServiceInstance[] responseBody = rest
				.getForEntity(properties.getDiscoveryServerUrl() + "/apps/" + serviceId, KubernetesServiceInstance[].class)
				.getBody();
		if (responseBody != null && responseBody.length > 0) {
			response = Arrays.stream(responseBody).filter(this::matchNamespaces).collect(Collectors.toList());
		}
		return response;
	}
	@Override
	public List<String> getServices() {
		List<String> response = Collections.emptyList();
		Service[] services = rest.getForEntity(properties.getDiscoveryServerUrl() + "/apps", Service[].class).getBody();
		if (services != null && services.length > 0) {
			response = Arrays.stream(services).filter(this::matchNamespaces).map(Service::getName)
					.collect(Collectors.toList());
		}
		return response;
	}

	private boolean matchNamespaces(KubernetesServiceInstance kubernetesServiceInstance) {
		if (CollectionUtils.isEmpty(properties.getNamespaces())) {
			return true;
		}
		return properties.getNamespaces().contains(kubernetesServiceInstance.getNamespace());
	}

	private boolean matchNamespaces(Service service) {
		if (CollectionUtils.isEmpty(service.getServiceInstances())) {
			return true;
		}
		return service.getServiceInstances().stream().anyMatch(this::matchNamespaces);
	}

}
