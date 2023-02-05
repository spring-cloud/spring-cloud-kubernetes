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
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.spring.extended.controller.config.KubernetesInformerAutoConfiguration;

import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

import static io.kubernetes.client.util.Namespaces.NAMESPACE_ALL;
import static io.kubernetes.client.util.Namespaces.NAMESPACE_DEFAULT;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class,
		// So that CatalogSharedInformerFactory can be processed prior to the default
		// factory
		KubernetesInformerAutoConfiguration.class })
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class })
public class KubernetesInformerDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
		LogFactory.getLog(KubernetesInformerDiscoveryClientAutoConfiguration.class));

	@Bean
	@ConditionalOnClass({ HealthIndicator.class })
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	public KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(
			ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

	@Bean
	@ConditionalOnMissingBean
	public KubernetesInformerDiscoveryClient kubernetesInformerDiscoveryClient(
			KubernetesNamespaceProvider kubernetesNamespaceProvider,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerDiscoveryClient(kubernetesNamespaceProvider.getNamespace(),
				serviceLister, endpointsLister, serviceInformer, endpointsInformer, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public SharedInformerFactory sharedInformerFactory(ApiClient client) {
		return new SharedInformerFactory(client);
	}

	@Bean
	@ConditionalOnMissingBean
	public SharedIndexInformer<V1Service> serviceSharedIndexInformer(SharedInformerFactory sharedInformerFactory,
			ApiClient apiClient, KubernetesNamespaceProvider kubernetesNamespaceProvider,
			KubernetesDiscoveryProperties discoveryProperties) {

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

		GenericKubernetesApi<V1Service, V1ServiceList> servicesApi = new GenericKubernetesApi<>(
			V1Service.class, V1ServiceList.class, "", "v1", "services", apiClient);

		return sharedInformerFactory.sharedIndexInformerFor(
			servicesApi, V1Service.class, 0L, namespace
		);
	}

	@Bean
	@ConditionalOnMissingBean
	public Lister<V1Service> serviceLister(SharedIndexInformer<V1Service> serviceSharedIndexInformer) {
		return new Lister<>(serviceSharedIndexInformer.getIndexer());
	}

}
