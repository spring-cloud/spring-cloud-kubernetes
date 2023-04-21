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

package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

import static io.kubernetes.client.util.Namespaces.NAMESPACE_ALL;
import static io.kubernetes.client.util.Namespaces.NAMESPACE_DEFAULT;

/**
 * This configuration is not used by us internally and will be removed in a future
 * release. Use it at your own risk.
 *
 * @author wind57
 */
@Deprecated(forRemoval = true)
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@ConditionalOnBlockingOrReactiveEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@Conditional(ConditionalOnSelectiveNamespacesMissing.class)
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class })
public class KubernetesInformerAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesInformerAutoConfiguration.class));

	@Bean
	@ConditionalOnMissingBean
	public SharedInformerFactory sharedInformerFactory(ApiClient client) {
		return new SharedInformerFactory(client);
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Service.class, parameterizedContainer = SharedIndexInformer.class)
	public SharedIndexInformer<V1Service> servicesSharedIndexInformer(SharedInformerFactory sharedInformerFactory,
			ApiClient apiClient, KubernetesNamespaceProvider kubernetesNamespaceProvider,
			KubernetesDiscoveryProperties discoveryProperties) {

		GenericKubernetesApi<V1Service, V1ServiceList> servicesApi = new GenericKubernetesApi<>(V1Service.class,
				V1ServiceList.class, "", "v1", "services", apiClient);

		return sharedInformerFactory.sharedIndexInformerFor(servicesApi, V1Service.class, 0L,
				namespace(discoveryProperties, kubernetesNamespaceProvider));
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Endpoints.class, parameterizedContainer = SharedIndexInformer.class)
	public SharedIndexInformer<V1Endpoints> endpointsSharedIndexInformer(SharedInformerFactory sharedInformerFactory,
			ApiClient apiClient, KubernetesNamespaceProvider kubernetesNamespaceProvider,
			KubernetesDiscoveryProperties discoveryProperties) {

		GenericKubernetesApi<V1Endpoints, V1EndpointsList> servicesApi = new GenericKubernetesApi<>(V1Endpoints.class,
				V1EndpointsList.class, "", "v1", "endpoints", apiClient);

		return sharedInformerFactory.sharedIndexInformerFor(servicesApi, V1Endpoints.class, 0L,
				namespace(discoveryProperties, kubernetesNamespaceProvider));
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Service.class, parameterizedContainer = Lister.class)
	public Lister<V1Service> servicesLister(SharedIndexInformer<V1Service> servicesSharedIndexInformer) {
		return new Lister<>(servicesSharedIndexInformer.getIndexer());
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Endpoints.class, parameterizedContainer = Lister.class)
	public Lister<V1Endpoints> endpointsLister(SharedIndexInformer<V1Endpoints> endpointsSharedIndexInformer) {
		return new Lister<>(endpointsSharedIndexInformer.getIndexer());
	}

	private String namespace(KubernetesDiscoveryProperties discoveryProperties,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		String namespace;
		if (discoveryProperties.allNamespaces()) {
			namespace = NAMESPACE_ALL;
		}
		else if (kubernetesNamespaceProvider.getNamespace() == null) {
			namespace = NAMESPACE_DEFAULT;
		}
		else {
			namespace = kubernetesNamespaceProvider.getNamespace();
		}

		LOG.debug(() -> "serviceSharedInformer will use namespace : " + namespace);
		return namespace;
	}

}
