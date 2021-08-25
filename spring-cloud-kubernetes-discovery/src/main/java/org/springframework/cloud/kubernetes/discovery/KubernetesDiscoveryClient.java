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
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestTemplate;

/**
 * @author Ryan Baxter
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private RestTemplate rest;

	private KubernetesDiscoveryClientProperties properties;

	public KubernetesDiscoveryClient(RestTemplate rest, KubernetesDiscoveryClientProperties properties) {
		this.rest = rest;
		this.properties = properties;
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	@Override
	@Cacheable("serviceinstances")
	public List<ServiceInstance> getInstances(String serviceId) {
		return Arrays.asList(rest.getForEntity(properties.getDiscoveryServerUrl() + "/apps/" + serviceId,
				KubernetesServiceInstance[].class).getBody());
	}

	@Override
	@Cacheable("services")
	public List<String> getServices() {
		return Arrays.stream(rest.getForEntity(properties.getDiscoveryServerUrl() + "/apps", Service[].class).getBody())
				.map(service -> service.getName()).collect(Collectors.toList());
	}

}
