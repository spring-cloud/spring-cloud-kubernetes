/*
 * Copyright 2012-2023 the original author or authors.
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
package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientUtils.endpoints;

/**
 * Provides instances of services that have "spec.type: ExternalName".
 *
 * @author wind57
 */
final class Fabric8ExternalNameServiceProvider {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8ExternalNameServiceProvider.class));

	private final KubernetesDiscoveryProperties properties = null;

	private final KubernetesClient client = null;

	private Fabric8ExternalNameServiceProvider() {

	}

	List<ServiceInstance> externalServiceInstances() {
		if (!properties.includeExternalNameServices()) {
			LOG.debug(() -> "skipping external name services look-up");
			return List.of();
		}

		if (properties.allNamespaces()) {
			LOG.debug(() -> "searching for endpoints in all namespaces");
			return client.services().inAnyNamespace().withNewFilter(), properties;
		}
//		else if (properties.namespaces().isEmpty()) {
//			LOG.debug(() -> "searching for endpoints in namespace : " + client.getNamespace());
//			return endpoints(client.endpoints().withNewFilter(), properties, serviceId);
//		}
//		else {
//			LOG.debug(() -> "searching for endpoints in namespaces : " + properties.namespaces());
//			List<Endpoints> endpoints = new ArrayList<>();
//			for (String namespace : properties.namespaces()) {
//				endpoints.addAll(
//					endpoints(client.endpoints().inNamespace(namespace).withNewFilter(), properties, serviceId));
//			}
//			return endpoints;
//		}
	}

}


