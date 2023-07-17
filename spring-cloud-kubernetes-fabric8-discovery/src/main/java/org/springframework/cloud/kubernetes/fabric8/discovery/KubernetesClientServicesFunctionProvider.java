/*
 * Copyright 2013-2023 the original author or authors.
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

import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.core.env.Environment;

/**
 * @author wind57
 */
public final class KubernetesClientServicesFunctionProvider {

	private KubernetesClientServicesFunctionProvider() {
	}

	public static KubernetesClientServicesFunction servicesFunction(KubernetesDiscoveryProperties properties,
			Environment environment) {

		if (properties.allNamespaces()) {
			return (client) -> client.services().inAnyNamespace().withLabels(properties.serviceLabels());
		}

		return client -> {
			String namespace = Fabric8Utils.getApplicationNamespace(client, null, "discovery-service",
					new KubernetesNamespaceProvider(environment));
			return client.services().inNamespace(namespace).withLabels(properties.serviceLabels());
		};

	}

	public static KubernetesClientServicesFunction servicesFunction(KubernetesDiscoveryProperties properties,
			Binder binder, BindHandler bindHandler) {

		if (properties.allNamespaces()) {
			return (client) -> client.services().inAnyNamespace().withLabels(properties.serviceLabels());
		}

		return client -> {
			String namespace = Fabric8Utils.getApplicationNamespace(client, null, "discovery-service",
					new KubernetesNamespaceProvider(binder, bindHandler));
			return client.services().inNamespace(namespace).withLabels(properties.serviceLabels());
		};

	}

}
