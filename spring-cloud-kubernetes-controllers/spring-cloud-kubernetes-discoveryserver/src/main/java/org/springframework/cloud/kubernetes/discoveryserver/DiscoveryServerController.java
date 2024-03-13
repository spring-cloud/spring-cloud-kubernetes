/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.discoveryserver;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ryan Baxter
 */
@RestController
public class DiscoveryServerController {

	private final KubernetesInformerReactiveDiscoveryClient reactiveDiscoveryClient;

	public DiscoveryServerController(KubernetesInformerReactiveDiscoveryClient reactiveDiscoveryClient) {
		this.reactiveDiscoveryClient = reactiveDiscoveryClient;
	}

	@GetMapping("/apps")
	public Flux<Service> apps() {
		return reactiveDiscoveryClient.getServices()
				.flatMap(service -> reactiveDiscoveryClient.getInstances(service).collectList()
						.flatMap(serviceInstances -> Mono.just(new Service(service,
								serviceInstances.stream().map(x -> (DefaultKubernetesServiceInstance) x).toList()))));
	}

	@GetMapping("/apps/{name}")
	public Flux<ServiceInstance> appInstances(@PathVariable String name) {
		return reactiveDiscoveryClient.getInstances(name);
	}

	/**
	 * use the "appInstanceNonDeprecated" instead.
	 */
	@Deprecated(forRemoval = true)
	@GetMapping("/app/{name}/{instanceId}")
	public Mono<ServiceInstance> appInstance(@PathVariable String name, @PathVariable String instanceId) {
		return innerAppInstance(name, instanceId);
	}

	@GetMapping("/apps/{name}/{instanceId}")
	Mono<ServiceInstance> appInstanceNonDeprecated(@PathVariable String name, @PathVariable String instanceId) {
		return innerAppInstance(name, instanceId);
	}

	private Mono<ServiceInstance> innerAppInstance(String name, String instanceId) {
		return reactiveDiscoveryClient.getInstances(name)
				.filter(serviceInstance -> serviceInstance.getInstanceId().equals(instanceId)).singleOrEmpty();
	}

}
