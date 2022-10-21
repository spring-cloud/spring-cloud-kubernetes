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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Implementation of {@link ServiceInstanceListSupplier} for load balancer in SERVICE
 * mode.
 *
 * @author Piotr Minkowski
 */
public class Fabric8ServicesListSupplier extends KubernetesServicesListSupplier {

	private final KubernetesClient kubernetesClient;

	Fabric8ServicesListSupplier(Environment environment, KubernetesClient kubernetesClient,
			Fabric8ServiceInstanceMapper mapper, KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, mapper, discoveryProperties);
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		List<ServiceInstance> result = new ArrayList<>();
		if (discoveryProperties.allNamespaces()) {
			List<Service> services = this.kubernetesClient.services().inAnyNamespace()
					.withField("metadata.name", this.getServiceId()).list().getItems();
			services.forEach(service -> result.add(mapper.map(service)));
		}
		else {
			Service service = StringUtils.hasText(this.kubernetesClient.getNamespace())
					? this.kubernetesClient.services().inNamespace(this.kubernetesClient.getNamespace())
							.withName(this.getServiceId()).get()
					: this.kubernetesClient.services().withName(this.getServiceId()).get();
			if (service != null) {
				result.add(mapper.map(service));
			}
		}
		return Flux.defer(() -> Flux.just(result));
	}

}
