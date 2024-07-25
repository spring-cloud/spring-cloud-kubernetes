/*
 * Copyright 2013-2024 the original author or authors.
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
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

/**
 * Implementation of {@link ServiceInstanceListSupplier} for load balancer in SERVICE
 * mode.
 *
 * @author Piotr Minkowski
 */
public class Fabric8ServicesListSupplier extends KubernetesServicesListSupplier<Service> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8ServicesListSupplier.class));

	private final KubernetesClient kubernetesClient;

	private final KubernetesNamespaceProvider namespaceProvider;

	Fabric8ServicesListSupplier(Environment environment, KubernetesClient kubernetesClient,
			Fabric8ServiceInstanceMapper mapper, KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, mapper, discoveryProperties);
		this.kubernetesClient = kubernetesClient;
		namespaceProvider = new KubernetesNamespaceProvider(environment);
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		List<ServiceInstance> result = new ArrayList<>();
		String serviceName = getServiceId();
		LOG.debug(() -> "serviceID : " + serviceName);

		if (discoveryProperties.allNamespaces()) {
			LOG.debug(() -> "discovering services in all namespaces");
			List<Service> services = kubernetesClient.services()
				.inAnyNamespace()
				.withField("metadata.name", serviceName)
				.list()
				.getItems();
			services.forEach(service -> addMappedService(mapper, result, service));
		}
		else if (!discoveryProperties.namespaces().isEmpty()) {
			List<String> selectiveNamespaces = discoveryProperties.namespaces().stream().sorted().toList();
			LOG.debug(() -> "discovering services in selective namespaces : " + selectiveNamespaces);
			selectiveNamespaces.forEach(selectiveNamespace -> {
				Service service = kubernetesClient.services()
					.inNamespace(selectiveNamespace)
					.withName(serviceName)
					.get();
				if (service != null) {
					addMappedService(mapper, result, service);
				}
				else {
					LOG.debug(() -> "did not find service with name : " + serviceName + " in namespace : "
							+ selectiveNamespace);
				}
			});
		}
		else {
			String namespace = Fabric8Utils.getApplicationNamespace(kubernetesClient, null, "loadbalancer-service",
					namespaceProvider);
			LOG.debug(() -> "discovering services in namespace : " + namespace);
			Service service = kubernetesClient.services().inNamespace(namespace).withName(serviceName).get();
			if (service != null) {
				addMappedService(mapper, result, service);
			}
			else {
				LOG.debug(() -> "did not find service with name : " + serviceName + " in namespace : " + namespace);
			}
		}

		LOG.debug(() -> "found services : " + result);
		return Flux.defer(() -> Flux.just(result));
	}

	private void addMappedService(KubernetesServiceInstanceMapper<Service> mapper, List<ServiceInstance> services,
			Service service) {
		services.add(mapper.map(service));
	}

}
