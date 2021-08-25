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

package org.springframework.cloud.kubernetes.reactive.discoveryclient.it;

import java.util.List;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ryan Baxter
 */
@SpringBootApplication
@RestController
public class KubernetesReactiveDiscoveryClientApplicationIt {

	@Autowired
	ReactiveDiscoveryClient discoveryClient;

	public static void main(String[] args) {
		SpringApplication.run(KubernetesReactiveDiscoveryClientApplicationIt.class, args);
	}

	@GetMapping("/services")
	public Mono<List<String>> services() {
		return discoveryClient.getServices().collect(Collectors.toList());
	}

	@GetMapping("/service/{serviceId}")
	public Flux<ServiceInstance> service(@PathVariable String serviceId) {
		return discoveryClient.getInstances(serviceId);
	}

}
