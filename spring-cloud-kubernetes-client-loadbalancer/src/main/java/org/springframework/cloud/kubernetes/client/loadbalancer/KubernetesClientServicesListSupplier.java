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

package org.springframework.cloud.kubernetes.client.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.getApplicationNamespace;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientServicesListSupplier extends KubernetesServicesListSupplier<V1Service> {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientServicesListSupplier.class));

	private final CoreV1Api coreV1Api;

	private final KubernetesNamespaceProvider kubernetesNamespaceProvider;

	public KubernetesClientServicesListSupplier(Environment environment,
			KubernetesServiceInstanceMapper<V1Service> mapper, KubernetesDiscoveryProperties discoveryProperties,
			CoreV1Api coreV1Api, KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, mapper, discoveryProperties);
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		List<ServiceInstance> result = new ArrayList<>();
		String serviceName = getServiceId();
		LOG.debug(() -> "serviceID : " + serviceName);

		if (discoveryProperties.allNamespaces()) {
			LOG.debug(() -> "discovering services in all namespaces");
			List<V1Service> services = services(null, serviceName);
			services.forEach(service -> addMappedService(mapper, result, service));
		}
		else if (!discoveryProperties.namespaces().isEmpty()) {
			List<String> selectiveNamespaces = discoveryProperties.namespaces().stream().sorted().toList();
			LOG.debug(() -> "discovering services in selective namespaces : " + selectiveNamespaces);
			selectiveNamespaces.forEach(selectiveNamespace -> {
				List<V1Service> services = services(selectiveNamespace, serviceName);
				services.forEach(service -> addMappedService(mapper, result, service));
			});
		}
		else {
			String namespace = getApplicationNamespace(null, "loadbalancer-service", kubernetesNamespaceProvider);
			LOG.debug(() -> "discovering services in namespace : " + namespace);
			List<V1Service> services = services(namespace, serviceName);
			services.forEach(service -> addMappedService(mapper, result, service));
		}

		LOG.debug(() -> "found services : " + result);
		return Flux.defer(() -> Flux.just(result));
	}

	private void addMappedService(KubernetesServiceInstanceMapper<V1Service> mapper, List<ServiceInstance> services,
			V1Service service) {
		services.add(mapper.map(service));
	}

	private List<V1Service> services(String namespace, String serviceName) {
		if (namespace == null) {
			try {
				return coreV1Api
					.listServiceForAllNamespaces(null, null, "metadata.name=" + serviceName, null, null, null, null,
							null, null, null, null)
					.getItems();
			}
			catch (ApiException apiException) {
				LOG.warn(apiException, "Error retrieving services (in all namespaces) with name " + serviceName);
				return List.of();
			}
		}
		else {
			try {
				// there is going to be a single service here, if found
				return coreV1Api
					.listNamespacedService(namespace, null, null, null, "metadata.name=" + serviceName, null, null,
							null, null, null, null, null)
					.getItems();
			}
			catch (ApiException apiException) {
				LOG.warn(apiException,
						"Error retrieving service with name " + serviceName + " in namespace : " + namespace);
				return List.of();
			}
		}
	}

}
