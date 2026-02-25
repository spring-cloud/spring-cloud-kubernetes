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

import io.fabric8.kubernetes.api.model.Service;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServicesListSupplier;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
abstract class AbstractFabric8ServicesListSupplier extends KubernetesServicesListSupplier<Service> {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(AbstractFabric8ServicesListSupplier.class));

	AbstractFabric8ServicesListSupplier(Environment environment, KubernetesServiceInstanceMapper<Service> mapper,
			KubernetesDiscoveryProperties discoveryProperties) {
		super(environment, mapper, discoveryProperties);
	}

	void addMappedServices(List<ServiceInstance> serviceInstances, List<Service> services, String namespace,
			String field, String fieldValue) {

		if (services.isEmpty()) {
			if (namespace == null) {
				LOG.debug(
						() -> "did not find any services in any namespace with " + field + " equal to : " + fieldValue);
			}
			else {
				LOG.debug(() -> "did not find any services in namespace " + namespace + " with " + field
						+ " equal to : " + fieldValue);
			}
		}
		else {
			services.forEach(service -> serviceInstances.add(mapper.map(service)));
		}

	}

}
