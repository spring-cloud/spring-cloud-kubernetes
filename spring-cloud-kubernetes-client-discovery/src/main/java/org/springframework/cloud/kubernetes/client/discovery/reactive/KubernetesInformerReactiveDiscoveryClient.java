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

package org.springframework.cloud.kubernetes.client.discovery.reactive;

import java.util.Objects;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author Ryan Baxter
 */
public class KubernetesInformerReactiveDiscoveryClient implements ReactiveDiscoveryClient {

	private final KubernetesInformerDiscoveryClient kubernetesDiscoveryClient;

	@Deprecated(forRemoval = true)
	public KubernetesInformerReactiveDiscoveryClient(KubernetesNamespaceProvider kubernetesNamespaceProvider,
			SharedInformerFactory sharedInformerFactory, Lister<V1Service> serviceLister,
			Lister<V1Endpoints> endpointsLister, SharedInformer<V1Service> serviceInformer,
			SharedInformer<V1Endpoints> endpointsInformer, KubernetesDiscoveryProperties properties) {
		this.kubernetesDiscoveryClient = new KubernetesInformerDiscoveryClient(
				kubernetesNamespaceProvider.getNamespace(), sharedInformerFactory, serviceLister, endpointsLister,
				serviceInformer, endpointsInformer, properties);
	}

	// this is either kubernetesClientInformerDiscoveryClient
	// or selectiveNamespacesKubernetesClientInformerDiscoveryClient
	KubernetesInformerReactiveDiscoveryClient(KubernetesInformerDiscoveryClient kubernetesDiscoveryClient) {
		this.kubernetesDiscoveryClient = kubernetesDiscoveryClient;
	}

	@Override
	public String description() {
		return "Kubernetes Reactive Discovery Client";
	}

	@Override
	public Flux<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId must be provided");
		return Flux.defer(() -> Flux.fromIterable(kubernetesDiscoveryClient.getInstances(serviceId)))
			.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Flux<String> getServices() {
		return Flux.defer(() -> Flux.fromIterable(kubernetesDiscoveryClient.getServices()))
			.subscribeOn(Schedulers.boundedElastic());
	}

}
