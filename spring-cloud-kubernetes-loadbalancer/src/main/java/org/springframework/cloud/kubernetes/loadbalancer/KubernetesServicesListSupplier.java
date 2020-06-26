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

package org.springframework.cloud.kubernetes.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang.StringUtils;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.env.Environment;

/**
 * @author Piotr Minkowski
 */
public class KubernetesServicesListSupplier implements ServiceInstanceListSupplier {

	private Environment environment;

	private KubernetesClient kubernetesClient;

	private KubernetesServiceInstanceMapper mapper;

	KubernetesServicesListSupplier(Environment environment,
			KubernetesClient kubernetesClient, KubernetesServiceInstanceMapper mapper) {
		this.environment = environment;
		this.kubernetesClient = kubernetesClient;
		this.mapper = mapper;
	}

	@Override
	public String getServiceId() {
		return environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		List<ServiceInstance> result = new ArrayList<>();
		Service service = StringUtils.isNotBlank(this.kubernetesClient.getNamespace())
				? this.kubernetesClient
						.services().inNamespace(this.kubernetesClient.getNamespace())
						.withName(this.getServiceId()).get()
				: this.kubernetesClient.services().withName(this.getServiceId()).get();
		if (service != null) {
			result.add(mapper.map(service));
		}
		return Flux.just(result);
	}

}
