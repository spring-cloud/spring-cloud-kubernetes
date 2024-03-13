/*
 * Copyright 2019-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Collections;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.Namespaces;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver;
import org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver.PropertyResolver;
import org.springframework.cloud.config.client.ConfigServerInstanceProvider;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.KubernetesConfigServerBootstrapper;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.kubernetesApiClient;

/**
 * @author Ryan Baxter
 */
class KubernetesClientConfigServerBootstrapper extends KubernetesConfigServerBootstrapper {

	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigServerBootstrapper.class);

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
		registry.registerIfAbsent(ConfigServerInstanceProvider.Function.class, context -> {
			if (!getDiscoveryEnabled(context)) {
				return (id) -> Collections.emptyList();
			}
			if (context.isRegistered(KubernetesInformerDiscoveryClient.class)) {
				KubernetesInformerDiscoveryClient client = context.get(KubernetesInformerDiscoveryClient.class);
				return client::getInstances;
			}
			else {
				PropertyResolver propertyResolver = getPropertyResolver(context);
				ApiClient defaultApiClient = kubernetesApiClient();
				defaultApiClient.setUserAgent(propertyResolver.get("spring.cloud.kubernetes.client.user-agent",
						String.class, KubernetesClientProperties.DEFAULT_USER_AGENT));
				KubernetesClientAutoConfiguration clientAutoConfiguration = new KubernetesClientAutoConfiguration();
				ApiClient apiClient = context.getOrElseSupply(ApiClient.class, () -> defaultApiClient);

				KubernetesNamespaceProvider kubernetesNamespaceProvider = clientAutoConfiguration
						.kubernetesNamespaceProvider(getNamespaceEnvironment(propertyResolver));
				KubernetesDiscoveryProperties discoveryProperties = context.get(KubernetesDiscoveryProperties.class);
				String namespace = getInformerNamespace(kubernetesNamespaceProvider, discoveryProperties);
				SharedInformerFactory sharedInformerFactory = new SharedInformerFactory(apiClient);
				GenericKubernetesApi<V1Service, V1ServiceList> servicesApi = new GenericKubernetesApi<>(V1Service.class,
						V1ServiceList.class, "", "v1", "services", apiClient);
				SharedIndexInformer<V1Service> serviceSharedIndexInformer = sharedInformerFactory
						.sharedIndexInformerFor(servicesApi, V1Service.class, 0L, namespace);
				Lister<V1Service> serviceLister = new Lister<>(serviceSharedIndexInformer.getIndexer());
				GenericKubernetesApi<V1Endpoints, V1EndpointsList> endpointsApi = new GenericKubernetesApi<>(
						V1Endpoints.class, V1EndpointsList.class, "", "v1", "endpoints", apiClient);
				SharedIndexInformer<V1Endpoints> endpointsSharedIndexInformer = sharedInformerFactory
						.sharedIndexInformerFor(endpointsApi, V1Endpoints.class, 0L, namespace);
				Lister<V1Endpoints> endpointsLister = new Lister<>(endpointsSharedIndexInformer.getIndexer());
				KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
						sharedInformerFactory, serviceLister, endpointsLister, serviceSharedIndexInformer,
						endpointsSharedIndexInformer, discoveryProperties);
				try {
					discoveryClient.afterPropertiesSet();
					return discoveryClient::getInstances;
				}
				catch (Exception e) {
					LOG.warn("Error initiating informer discovery client", e);
					return (serviceId) -> Collections.emptyList();
				}
				finally {
					sharedInformerFactory.stopAllRegisteredInformers();
				}
			}
		});

	}

	private String getInformerNamespace(KubernetesNamespaceProvider kubernetesNamespaceProvider,
			KubernetesDiscoveryProperties discoveryProperties) {
		return discoveryProperties.allNamespaces() ? Namespaces.NAMESPACE_ALL
				: kubernetesNamespaceProvider.getNamespace() == null ? Namespaces.NAMESPACE_DEFAULT
						: kubernetesNamespaceProvider.getNamespace();
	}

	private Environment getNamespaceEnvironment(
			ConfigServerConfigDataLocationResolver.PropertyResolver propertyResolver) {
		return new AbstractEnvironment() {
			@Override
			public String getProperty(String key) {
				return propertyResolver.get(key, String.class, super.getProperty(key));
			}
		};
	}

}
