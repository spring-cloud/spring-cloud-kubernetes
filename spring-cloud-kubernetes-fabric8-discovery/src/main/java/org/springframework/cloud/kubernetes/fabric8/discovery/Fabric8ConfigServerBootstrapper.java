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

import java.util.Collections;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver.PropertyResolver;
import org.springframework.cloud.config.client.ConfigServerInstanceProvider;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.KubernetesConfigServerBootstrapper;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;

/**
 * @author Ryan Baxter
 */
class Fabric8ConfigServerBootstrapper extends KubernetesConfigServerBootstrapper {

	@Override
	public void initialize(BootstrapRegistry registry) {
		if (hasConfigServerInstanceProvider()) {
			return;
		}

		registry.registerIfAbsent(KubernetesDiscoveryProperties.class, context -> {
			if (!getDiscoveryEnabled(context)) {
				return null;
			}
			return createKubernetesDiscoveryProperties(context);
		});

		registry.registerIfAbsent(KubernetesClientProperties.class, context -> {
			if (!getDiscoveryEnabled(context)) {
				return null;
			}
			return createKubernetesClientProperties(context);
		});

		// create instance provider
		registry.registerIfAbsent(ConfigServerInstanceProvider.Function.class, context -> {
			if (!getDiscoveryEnabled(context)) {
				return (id) -> Collections.emptyList();
			}
			if (context.isRegistered(KubernetesDiscoveryClient.class)) {
				KubernetesDiscoveryClient client = context.get(KubernetesDiscoveryClient.class);
				return client::getInstances;
			}
			else {
				PropertyResolver propertyResolver = getPropertyResolver(context);
				Fabric8AutoConfiguration fabric8AutoConfiguration = new Fabric8AutoConfiguration();
				Config config = fabric8AutoConfiguration
					.kubernetesClientConfig(context.get(KubernetesClientProperties.class));
				KubernetesClient kubernetesClient = fabric8AutoConfiguration.kubernetesClient(config);
				KubernetesDiscoveryProperties discoveryProperties = context.get(KubernetesDiscoveryProperties.class);
				KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(kubernetesClient,
						discoveryProperties,
						KubernetesClientServicesFunctionProvider.servicesFunction(discoveryProperties,
								new KubernetesNamespaceProvider(propertyResolver
									.get(KubernetesNamespaceProvider.NAMESPACE_PROPERTY, String.class, null))),
						null, new ServicePortSecureResolver(discoveryProperties));
				return discoveryClient::getInstances;
			}
		});
	}

}
