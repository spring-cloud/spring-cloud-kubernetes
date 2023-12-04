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

import reactor.core.publisher.Flux;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 */
public class KubernetesReactiveDiscoveryClient implements ReactiveDiscoveryClient {

	private final WebClient webClient;

	@Deprecated(forRemoval = true)
	public KubernetesReactiveDiscoveryClient(WebClient.Builder webClientBuilder,
			KubernetesDiscoveryClientProperties properties) {
		if (!StringUtils.hasText(properties.getDiscoveryServerUrl())) {
			throw new DiscoveryServerUrlInvalidException();
		}
		this.webClient = webClientBuilder.baseUrl(properties.getDiscoveryServerUrl()).build();
	}

	KubernetesReactiveDiscoveryClient(WebClient.Builder webClientBuilder, KubernetesDiscoveryProperties properties) {
		if (!StringUtils.hasText(properties.discoveryServerUrl())) {
			throw new DiscoveryServerUrlInvalidException();
		}
		this.webClient = webClientBuilder.baseUrl(properties.discoveryServerUrl()).build();
	}

	@Override
	public String description() {
		return "Reactive Kubernetes Discovery Client";
	}

	@Override
	@Cacheable("serviceinstances")
	public Flux<ServiceInstance> getInstances(String serviceId) {
		return webClient.get().uri("/apps/" + serviceId)
				.exchangeToFlux(clientResponse -> clientResponse.bodyToFlux(KubernetesServiceInstance.class));
	}

	@Override
	@Cacheable("services")
	public Flux<String> getServices() {
		return webClient.get().uri("/apps").exchangeToFlux(
				clientResponse -> clientResponse.bodyToFlux(Service.class).map(service -> service.getName()));
	}

}
