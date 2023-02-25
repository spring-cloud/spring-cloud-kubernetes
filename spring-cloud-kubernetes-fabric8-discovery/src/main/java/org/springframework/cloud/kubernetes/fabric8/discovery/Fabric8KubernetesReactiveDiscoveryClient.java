/*
 * Copyright 2019-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.util.Assert;

/**
 * Kubernetes implementation of {@link ReactiveDiscoveryClient}. Currently relies on the
 * {@link Fabric8KubernetesDiscoveryClient} for feature parity.
 *
 * @author Tim Ysewyn
 */
final class Fabric8KubernetesReactiveDiscoveryClient implements ReactiveDiscoveryClient {

	private final Fabric8KubernetesDiscoveryClient fabric8KubernetesDiscoveryClient;

	Fabric8KubernetesReactiveDiscoveryClient(Fabric8KubernetesDiscoveryClient fabric8KubernetesDiscoveryClient) {
		this.fabric8KubernetesDiscoveryClient = fabric8KubernetesDiscoveryClient;
	}

	@Override
	public String description() {
		return "Kubernetes Reactive Discovery Client";
	}

	@Override
	public Flux<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId, "[Assertion failed] - the object argument must not be null");
		return Flux.defer(() -> Flux.fromIterable(fabric8KubernetesDiscoveryClient.getInstances(serviceId)))
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Flux<String> getServices() {
		return Flux.defer(() -> Flux.fromIterable(fabric8KubernetesDiscoveryClient.getServices()))
				.subscribeOn(Schedulers.boundedElastic());
	}

}
