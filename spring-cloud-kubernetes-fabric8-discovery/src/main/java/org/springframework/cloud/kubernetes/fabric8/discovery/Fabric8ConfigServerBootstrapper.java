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
import java.util.List;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;

import org.springframework.boot.BootstrapContext;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.config.client.ConfigServerInstanceProvider;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.KubernetesConfigServerBootstrapper;
import org.springframework.cloud.kubernetes.commons.config.KubernetesConfigServerInstanceProvider;
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
		// We need to pass a lambda here rather than create a new instance of
		// ConfigServerInstanceProvider.Function
		// or else we will get ClassNotFoundExceptions if Spring Cloud Config is not on
		// the classpath
		registry.registerIfAbsent(ConfigServerInstanceProvider.Function.class, KubernetesFunction::create);
	}

	final static class KubernetesFunction implements ConfigServerInstanceProvider.Function {

		private final BootstrapContext context;

		private KubernetesFunction(BootstrapContext context) {
			this.context = context;
		}

		static KubernetesFunction create(BootstrapContext context) {
			return new KubernetesFunction(context);
		}

		@Override
		public List<ServiceInstance> apply(String serviceId, Binder binder, BindHandler bindHandler, Log log) {
			if (binder == null || bindHandler == null || !getDiscoveryEnabled(binder, bindHandler)) {
				// If we don't have the Binder or BinderHandler from the
				// ConfigDataLocationResolverContext
				// we won't be able to create the necessary configuration
				// properties to configure the
				// Kubernetes DiscoveryClient
				return Collections.emptyList();
			}
			KubernetesDiscoveryProperties discoveryProperties = createKubernetesDiscoveryProperties(binder,
					bindHandler);
			KubernetesClientProperties clientProperties = createKubernetesClientProperties(binder, bindHandler);
			return getInstanceProvider(discoveryProperties, clientProperties, context, binder, bindHandler)
					.getInstances(serviceId);
		}

		private KubernetesConfigServerInstanceProvider getInstanceProvider(
				KubernetesDiscoveryProperties discoveryProperties, KubernetesClientProperties clientProperties,
				BootstrapContext context, Binder binder, BindHandler bindHandler) {
			if (context.isRegistered(KubernetesDiscoveryClient.class)) {
				KubernetesDiscoveryClient client = context.get(KubernetesDiscoveryClient.class);
				return client::getInstances;
			}
			else {
				Fabric8AutoConfiguration fabric8AutoConfiguration = new Fabric8AutoConfiguration();
				Config config = fabric8AutoConfiguration.kubernetesClientConfig(clientProperties);
				KubernetesClient kubernetesClient = fabric8AutoConfiguration.kubernetesClient(config);
				KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(kubernetesClient,
						discoveryProperties, KubernetesClientServicesFunctionProvider
								.servicesFunction(discoveryProperties, binder, bindHandler),
						null, new ServicePortSecureResolver(discoveryProperties));
				return discoveryClient::getInstances;
			}
		}

		// This method should never be called, but is there for backward
		// compatibility purposes
		@Override
		public List<ServiceInstance> apply(String serviceId) {
			return apply(serviceId, null, null, null);
		}

	}

}
