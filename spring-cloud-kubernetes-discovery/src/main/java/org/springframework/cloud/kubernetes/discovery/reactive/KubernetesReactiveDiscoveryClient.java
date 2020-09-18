/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery.reactive;

import io.fabric8.kubernetes.client.KubernetesClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.discovery.KubernetesClientServicesFunction;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryClient;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryProperties;
import org.springframework.util.Assert;

/**
 * Kubernetes implementation of {@link ReactiveDiscoveryClient}. Currently relies on the
 * {@link KubernetesDiscoveryClient} for feature parity.
 *
 * @author Tim Ysewyn
 */
public class KubernetesReactiveDiscoveryClient implements ReactiveDiscoveryClient {

	private final KubernetesDiscoveryClient kubernetesDiscoveryClient;

	public KubernetesReactiveDiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties properties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction) {
		this.kubernetesDiscoveryClient = new KubernetesDiscoveryClient(client, properties,
				kubernetesClientServicesFunction);
	}

	@Override
	public String description() {
		return "Kubernetes Reactive Discovery Client";
	}

	@Override
	public Flux<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId, "[Assertion failed] - the object argument must not be null");
		return Flux.defer(() -> Flux.fromIterable(kubernetesDiscoveryClient.getInstances(serviceId)))
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Flux<String> getServices() {
		return Flux.defer(() -> Flux.fromIterable(kubernetesDiscoveryClient.getServices()))
				.subscribeOn(Schedulers.boundedElastic());
	}

}
