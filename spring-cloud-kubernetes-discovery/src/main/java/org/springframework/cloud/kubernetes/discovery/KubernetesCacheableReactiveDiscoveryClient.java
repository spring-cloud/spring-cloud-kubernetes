/*
 * Copyright 2013-present the original author or authors.
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
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class KubernetesCacheableReactiveDiscoveryClient extends KubernetesAbstractReactiveDiscoveryClient {

	KubernetesCacheableReactiveDiscoveryClient(WebClient.Builder webClientBuilder,
			KubernetesDiscoveryProperties properties) {
		super(webClientBuilder, properties);
	}

	@Override
	@Cacheable("k8s-reactive-discovery-services")
	public Flux<String> getServices() {
		return super.getServices();
	}

	@Override
	@Cacheable("k8s-reactive-discovery-instances")
	public Flux<ServiceInstance> getInstances(String serviceId) {
		return super.getInstances(serviceId);
	}

	@Override
	public String description() {
		return "Reactive Cacheable Kubernetes Discovery Client";
	}

}
