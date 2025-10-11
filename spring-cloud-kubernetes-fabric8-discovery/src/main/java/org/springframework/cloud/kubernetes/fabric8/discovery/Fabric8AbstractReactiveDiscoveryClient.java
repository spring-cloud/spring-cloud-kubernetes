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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Objects;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;

/**
 * @author wind57
 */
abstract class Fabric8AbstractReactiveDiscoveryClient implements ReactiveDiscoveryClient {

	private final Fabric8DiscoveryClient fabric8DiscoveryClient;

	Fabric8AbstractReactiveDiscoveryClient(Fabric8DiscoveryClient fabric8DiscoveryClient) {
		this.fabric8DiscoveryClient = fabric8DiscoveryClient;
	}

	@Override
	public Flux<String> getServices() {
		return Flux.defer(() -> Flux.fromIterable(fabric8DiscoveryClient.getServices()))
			.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Flux<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId must not be null");
		return Flux.defer(() -> Flux.fromIterable(fabric8DiscoveryClient.getInstances(serviceId)))
			.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public abstract String description();

	@Override
	public int getOrder() {
		return fabric8DiscoveryClient.getOrder();
	}

}
