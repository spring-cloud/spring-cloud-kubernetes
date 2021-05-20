/*
 * Copyright 2013-2019 the original author or authors.
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

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.spring.extended.controller.annotation.GroupVersionResource;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;
import io.kubernetes.client.spring.extended.controller.config.KubernetesInformerAutoConfiguration;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesDiscoveryEnabled
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class,
		// So that CatalogSharedInformerFactory can be processed in prior to the default
		// factory
		KubernetesInformerAutoConfiguration.class })
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class })
@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
public class KubernetesDiscoveryClientAutoConfiguration {

	@ConditionalOnClass({ HealthIndicator.class })
	@ConditionalOnDiscoveryEnabled
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	@Configuration
	public static class KubernetesDiscoveryClientHealthIndicatorConfiguration {

		@Bean
		public KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(
				ApplicationEventPublisher applicationEventPublisher, PodUtils podUtils) {
			return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBlockingDiscoveryEnabled
	public static class KubernetesInformerDiscoveryConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public SpringCloudKubernetesInformerFactoryProcessor discoveryInformerConfigurer(
				KubernetesNamespaceProvider kubernetesNamespaceProvider,
				KubernetesDiscoveryProperties kubernetesDiscoveryProperties, ApiClient apiClient,
				CatalogSharedInformerFactory sharedInformerFactory) {
			return new SpringCloudKubernetesInformerFactoryProcessor(kubernetesDiscoveryProperties,
					kubernetesNamespaceProvider, apiClient, sharedInformerFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		public CatalogSharedInformerFactory catalogSharedInformerFactory(ApiClient apiClient) {
			return new CatalogSharedInformerFactory();
		}

		@Bean
		@ConditionalOnMissingBean
		public KubernetesInformerDiscoveryClient kubernetesInformerDiscoveryClient(
				KubernetesNamespaceProvider kubernetesNamespaceProvider,
				CatalogSharedInformerFactory sharedInformerFactory, Lister<V1Service> serviceLister,
				Lister<V1Endpoints> endpointsLister, SharedInformer<V1Service> serviceInformer,
				SharedInformer<V1Endpoints> endpointsInformer, KubernetesDiscoveryProperties properties) {
			return new KubernetesInformerDiscoveryClient(kubernetesNamespaceProvider.getNamespace(),
					sharedInformerFactory, serviceLister, endpointsLister, serviceInformer, endpointsInformer,
					properties);
		}

		@KubernetesInformers({
				@KubernetesInformer(apiTypeClass = V1Service.class, apiListTypeClass = V1ServiceList.class,
						groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
								resourcePlural = "services")),
				@KubernetesInformer(apiTypeClass = V1Endpoints.class, apiListTypeClass = V1EndpointsList.class,
						groupVersionResource = @GroupVersionResource(apiGroup = "", apiVersion = "v1",
								resourcePlural = "endpoints")) })
		class CatalogSharedInformerFactory extends SharedInformerFactory {

			// TODO: optimization to ease memory pressure from continuous list&watch.

		}

	}

}
