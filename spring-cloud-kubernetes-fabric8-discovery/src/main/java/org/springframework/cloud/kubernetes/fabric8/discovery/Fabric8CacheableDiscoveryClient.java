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

import java.util.List;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;

/**
 * Cacheable Fabric8 Kubernetes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 * @author Tim Ysewyn
 */
class Fabric8CacheableDiscoveryClient extends Fabric8AbstractBlockingDiscoveryClient {

	Fabric8CacheableDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			ServicePortSecureResolver servicePortSecureResolver, KubernetesNamespaceProvider namespaceProvider,
			Predicate<Service> predicate) {

		super(client, kubernetesDiscoveryProperties, servicePortSecureResolver, namespaceProvider, predicate);
	}

	@Override
	@Cacheable("fabric8-blocking-discovery-services")
	public List<String> getServices() {
		return super.getServices();
	}

	@Override
	@Cacheable("fabric8-blocking-discovery-instances")
	public List<ServiceInstance> getInstances(String serviceId) {
		return super.getInstances(serviceId);
	}

	@Override
	public String description() {
		return "Fabric8 Cacheable Blocking Discovery Client";
	}

	@Override
	public int getOrder() {
		return super.getOrder();
	}

}
