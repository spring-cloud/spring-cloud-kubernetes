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
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.log.LogAccessor;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.core.env.Environment;

class Fabric8LabelBasedServicesListSupplier extends AbstractFabric8ServicesListSupplier {

	private static final LogAccessor LOG = new LogAccessor(
		LogFactory.getLog(Fabric8LabelBasedServicesListSupplier.class));

	private final KubernetesDiscoveryProperties properties;

	Fabric8LabelBasedServicesListSupplier(Environment environment, KubernetesClient kubernetesClient,
			KubernetesServiceInstanceMapper<Service> mapper, KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, kubernetesClient, mapper, discoveryProperties);
		this.properties = discoveryProperties;
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		return Flux.defer(() -> {
			Map<String, String> serviceLabels = properties.serviceLabels();
			LOG.debug(() -> "loadbalancer service labels : " + serviceLabels);
			List<ServiceInstance> serviceInstances = serviceInstances("metadata.labels", labelSelector(serviceLabels));
			return Flux.just(serviceInstances);
		});
	}

	private static String labelSelector(Map<String, String> labels) {
		if (labels == null || labels.isEmpty()) {
			return null;
		}
		return labels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
	}

}
