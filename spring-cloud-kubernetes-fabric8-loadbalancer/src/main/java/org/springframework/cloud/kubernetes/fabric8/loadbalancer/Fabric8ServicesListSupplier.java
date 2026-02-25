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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import java.util.List;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

/**
 * Implementation of {@link ServiceInstanceListSupplier} for load balancer in SERVICE
 * mode.
 *
 * @author Piotr Minkowski
 */
public class Fabric8ServicesListSupplier extends AbstractFabric8ServicesListSupplier {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8ServicesListSupplier.class));

	Fabric8ServicesListSupplier(Environment environment, KubernetesClient kubernetesClient,
			Fabric8ServiceInstanceMapper mapper, KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, kubernetesClient, mapper, discoveryProperties);
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		return Flux.defer(() -> {
			String serviceName = getServiceId();
			LOG.debug(() -> "loadbalancer serviceID : " + serviceName);
			List<ServiceInstance> serviceInstances = serviceInstances("metadata.name", serviceName);
			return Flux.just(serviceInstances);
		});
	}

}
