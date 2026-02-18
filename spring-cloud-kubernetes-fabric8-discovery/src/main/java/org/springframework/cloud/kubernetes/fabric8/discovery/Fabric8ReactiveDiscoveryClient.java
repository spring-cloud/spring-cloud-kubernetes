/*
 * Copyright 2019-present the original author or authors.
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

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;

/**
 * Kubernetes implementation of {@link ReactiveDiscoveryClient}. Currently relies on the
 * {@link Fabric8DiscoveryClient} for feature parity.
 *
 * @author Tim Ysewyn
 */
public class Fabric8ReactiveDiscoveryClient extends Fabric8AbstractReactiveDiscoveryClient {

	public Fabric8ReactiveDiscoveryClient(Fabric8DiscoveryClient fabric8DiscoveryClient) {
		super(fabric8DiscoveryClient);
	}

	@Override
	public Flux<String> getServices() {
		return super.getServices();
	}

	@Override
	public Flux<ServiceInstance> getInstances(String serviceId) {
		return super.getInstances(serviceId);
	}

	@Override
	public String description() {
		return "Fabric8 Reactive Discovery Client";
	}

	@Override
	public int getOrder() {
		return super.getOrder();
	}

}
