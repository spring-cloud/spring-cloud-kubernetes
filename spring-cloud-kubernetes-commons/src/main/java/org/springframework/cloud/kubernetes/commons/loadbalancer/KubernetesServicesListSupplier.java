/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.loadbalancer;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.env.Environment;

/**
 * Implementation of {@link ServiceInstanceListSupplier} for load balancer in SERVICE
 * mode.
 *
 * @author Piotr Minkowski
 */
public abstract class KubernetesServicesListSupplier implements ServiceInstanceListSupplier {

	protected final Environment environment;

	protected final KubernetesDiscoveryProperties discoveryProperties;

	protected final KubernetesServiceInstanceMapper mapper;

	public KubernetesServicesListSupplier(Environment environment, KubernetesServiceInstanceMapper mapper,
			KubernetesDiscoveryProperties discoveryProperties) {
		this.environment = environment;
		this.discoveryProperties = discoveryProperties;
		this.mapper = mapper;
	}

	@Override
	public String getServiceId() {
		return environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
	}

	@Override
	public abstract Flux<List<ServiceInstance>> get();

}
