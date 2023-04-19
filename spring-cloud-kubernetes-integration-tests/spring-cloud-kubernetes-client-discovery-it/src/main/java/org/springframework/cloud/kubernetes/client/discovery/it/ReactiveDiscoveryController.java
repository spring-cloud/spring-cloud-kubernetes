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

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
public class ReactiveDiscoveryController {

	private final KubernetesInformerReactiveDiscoveryClient reactiveDiscoveryClient;

	public ReactiveDiscoveryController(
			ObjectProvider<KubernetesInformerReactiveDiscoveryClient> reactiveDiscoveryClient) {
		KubernetesInformerReactiveDiscoveryClient[] local = new KubernetesInformerReactiveDiscoveryClient[1];
		reactiveDiscoveryClient.ifAvailable(x -> local[0] = x);
		this.reactiveDiscoveryClient = local[0];
	}

	@GetMapping("/reactive/services")
	public Mono<List<String>> allServices() {
		return reactiveDiscoveryClient.getServices().collectList();
	}

}