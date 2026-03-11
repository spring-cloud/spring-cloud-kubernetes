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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.getApplicationNamespace;

/**
 * Implementation of {@link ServiceInstanceListSupplier} for load balancer in SERVICE mode
 * based on labels filtering.
 *
 * @author wind57
 */
public class Fabric8LabelBasedServicesListSupplier extends AbstractFabric8ServicesListSupplier {

	private static final LogAccessor LOG = new LogAccessor(Fabric8LabelBasedServicesListSupplier.class);

	private static final String FIELD_NAME = "metadata.labels";

	private final Map<String, String> serviceLabels;

	private final KubernetesClient kubernetesClient;

	private final KubernetesNamespaceProvider namespaceProvider;

	Fabric8LabelBasedServicesListSupplier(Environment environment, KubernetesClient kubernetesClient,
			Fabric8ServiceInstanceMapper mapper, KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, mapper, discoveryProperties);
		this.kubernetesClient = kubernetesClient;
		namespaceProvider = new KubernetesNamespaceProvider(environment);
		this.serviceLabels = discoveryProperties.serviceLabels();
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		return Flux.defer(() -> {
			List<ServiceInstance> serviceInstances = new ArrayList<>();

			if (discoveryProperties.allNamespaces()) {
				LOG.debug(() -> "discovering services in all namespaces");
				List<Service> services = kubernetesClient.services()
					.inAnyNamespace()
					.withLabels(serviceLabels)
					.list()
					.getItems();

				addMappedServices(serviceInstances, services, null, FIELD_NAME, serviceLabels.toString());
			}
			else if (!discoveryProperties.namespaces().isEmpty()) {
				List<String> selectiveNamespaces = discoveryProperties.namespaces().stream().sorted().toList();
				LOG.debug(() -> "discovering services in selective namespaces : " + selectiveNamespaces);
				selectiveNamespaces.forEach(selectiveNamespace -> {
					List<Service> services = kubernetesClient.services()
						.inNamespace(selectiveNamespace)
						.withLabels(serviceLabels)
						.list()
						.getItems();

					addMappedServices(serviceInstances, services, selectiveNamespace, FIELD_NAME, serviceLabels.toString());
				});
			}
			else {
				String namespace = getApplicationNamespace(kubernetesClient, null, "loadbalancer-service",
						namespaceProvider);
				LOG.debug(() -> "discovering services in namespace : " + namespace);
				List<Service> services = kubernetesClient.services()
					.inNamespace(namespace)
					.withLabels(serviceLabels)
					.list()
					.getItems();

				addMappedServices(serviceInstances, services, namespace, FIELD_NAME, serviceLabels.toString());
			}

			LOG.debug(() -> "found services : " + serviceInstances);
			return Flux.just(serviceInstances);
		});
	}

}
