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

package org.springframework.cloud.kubernetes.client.loadbalancer;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.core.env.Environment;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientServicesListSupplier extends KubernetesServicesListSupplier {

	private static final Log LOG = LogFactory.getLog(KubernetesClientServicesListSupplier.class);

	private CoreV1Api coreV1Api;

	private KubernetesClientProperties kubernetesClientProperties;

	private KubernetesNamespaceProvider kubernetesNamespaceProvider;

	public KubernetesClientServicesListSupplier(Environment environment, KubernetesServiceInstanceMapper mapper,
			KubernetesDiscoveryProperties discoveryProperties, CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(environment, mapper, discoveryProperties);
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	private String getNamespace() {
		return kubernetesNamespaceProvider != null ? kubernetesNamespaceProvider.getNamespace()
				: kubernetesClientProperties.getNamespace();
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		LOG.info("Getting services with id " + this.getServiceId());
		List<ServiceInstance> result = new ArrayList<>();
		List<V1Service> services = null;
		try {
			if (discoveryProperties.allNamespaces()) {
				services = coreV1Api.listServiceForAllNamespaces(null, null, "metadata.name=" + this.getServiceId(),
						null, null, null, null, null, null, null).getItems();
			}
			else {
				services = coreV1Api.listNamespacedService(getNamespace(), null, null, null,
						"metadata.name=" + this.getServiceId(), null, null, null, null, null, null).getItems();
			}
			services.forEach(service -> result.add(mapper.map(service)));
		}
		catch (ApiException e) {
			LOG.warn("Error retrieving service with name " + this.getServiceId(), e);
		}
		LOG.info("Returning services: " + result);
		return Flux.defer(() -> Flux.just(result));
	}

}
