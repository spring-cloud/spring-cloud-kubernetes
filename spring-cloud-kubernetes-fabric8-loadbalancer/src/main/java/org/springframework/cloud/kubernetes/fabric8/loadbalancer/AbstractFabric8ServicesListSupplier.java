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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.getApplicationNamespace;

/**
 * @author wind57
 */
abstract class AbstractFabric8ServicesListSupplier extends KubernetesServicesListSupplier<Service> {

	private static final LogAccessor LOG = new LogAccessor(AbstractFabric8ServicesListSupplier.class);

	private final KubernetesClient kubernetesClient;

	private final KubernetesNamespaceProvider namespaceProvider;

	AbstractFabric8ServicesListSupplier(Environment environment, KubernetesClient kubernetesClient,
			KubernetesServiceInstanceMapper<Service> mapper, KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, mapper, discoveryProperties);
		this.kubernetesClient = kubernetesClient;
		namespaceProvider = new KubernetesNamespaceProvider(environment);
	}

	/**
	 * <pre>
	 *     - accepts a field that we will filter for ( 'metadata.name' or 'metadata.labels' )
	 *       and a value for that field.
	 * </pre>
	 */
	List<ServiceInstance> serviceInstances(String field, String fieldValue) {

		List<ServiceInstance> serviceInstances = new ArrayList<>();

		LOG.debug(() -> "using field :" + field + " with value :" + fieldValue);

		if (discoveryProperties.allNamespaces()) {
			LOG.debug(() -> "discovering services in all namespaces");
			List<Service> services = kubernetesClient.services()
				.inAnyNamespace()
				.withField(field, fieldValue)
				.list()
				.getItems();

			addMappedServices(serviceInstances, services, null, field, fieldValue);
		}
		else if (!discoveryProperties.namespaces().isEmpty()) {
			List<String> selectiveNamespaces = discoveryProperties.namespaces().stream().sorted().toList();
			LOG.debug(() -> "discovering services in selective namespaces : " + selectiveNamespaces);
			selectiveNamespaces.forEach(selectiveNamespace -> {
				List<Service> services = kubernetesClient.services()
					.inNamespace(selectiveNamespace)
					.withField(field, fieldValue)
					.list()
					.getItems();

				addMappedServices(serviceInstances, services, selectiveNamespace, field, fieldValue);
			});
		}
		else {
			String namespace = getApplicationNamespace(kubernetesClient, null, "loadbalancer-service",
					namespaceProvider);
			LOG.debug(() -> "discovering services in namespace : " + namespace);
			List<Service> services = kubernetesClient.services()
				.inNamespace(namespace)
				.withField(field, fieldValue)
				.list()
				.getItems();

			addMappedServices(serviceInstances, services, namespace, field, fieldValue);
		}

		return serviceInstances;
	}

	private void addMappedServices(List<ServiceInstance> serviceInstances, List<Service> services, String namespace,
			String field, String fieldValue) {

		if (services.isEmpty()) {
			// meaning all-namespaces
			if (namespace == null) {
				LOG.debug(() -> "Did not find services with field : " + field + " " + "and fieldValue : " + fieldValue
						+ " in any namespace");
			}
			else {
				LOG.debug(() -> "Did not find services with field : " + field + " " + "and fieldValue : " + fieldValue
						+ " in namespace : " + namespace);
			}
		}
		else {
			services.forEach(service -> serviceInstances.add(mapper.map(service)));
		}
	}

}
