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

package org.springframework.cloud.kubernetes.client.discovery.it;

import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
public class KubernetesDiscoveryController {

	private final ReactiveDiscoveryClient discoveryClient;

	public KubernetesDiscoveryController(ReactiveDiscoveryClient discoveryClient) {
		System.out.println(" ====== " + discoveryClient.getClass().getName());
		this.discoveryClient = discoveryClient;
	}

	@GetMapping("/http/services")
	public Flux<String> services() {
		return discoveryClient.getServices();
	}

	@GetMapping("/http/service/{serviceId}")
	public Flux<ServiceInstance> service(@PathVariable String serviceId) {
		return discoveryClient.getInstances(serviceId);
	}

}
