/*
 * Copyright 2019-present the original author or authors.
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

import java.util.List;
import java.util.function.Predicate;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesReactiveDiscovery
@AutoConfigureBefore({ SimpleReactiveDiscoveryClientAutoConfiguration.class,
		ReactiveCommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ ReactiveCompositeDiscoveryClientAutoConfiguration.class,
		KubernetesDiscoveryPropertiesAutoConfiguration.class,
		KubernetesClientDiscoveryClientSpelAutoConfiguration.class,
		KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class })
public class KubernetesClientInformerReactiveHealthAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientInformerReactiveHealthAutoConfiguration.class));

	@Bean
	@ConditionalOnBean(KubernetesClientInformerReactiveDiscoveryClient.class)
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator nonCacheableReactiveDiscoveryClientHealthIndicator(
			KubernetesClientInformerReactiveDiscoveryClient reactiveClient,
			DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(reactiveClient, properties);
	}

	@Bean
	@ConditionalOnMissingBean(KubernetesClientInformerReactiveDiscoveryClient.class)
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator cacheableReactiveDiscoveryClientHealthIndicator(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedIndexInformer<V1Service>> serviceInformers,
			List<SharedIndexInformer<V1Endpoints>> endpointsInformers,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties, CoreV1Api coreV1Api,
			Predicate<V1Service> predicate, DiscoveryClientHealthIndicatorProperties properties) {

		KubernetesClientInformerDiscoveryClient blockingClient = new KubernetesClientInformerDiscoveryClient(
				sharedInformerFactories, serviceListers, endpointsListers, serviceInformers, endpointsInformers,
				kubernetesDiscoveryProperties, coreV1Api, predicate);
		blockingClient.afterPropertiesSet();

		KubernetesClientInformerReactiveDiscoveryClient reactiveClient = new KubernetesClientInformerReactiveDiscoveryClient(
				blockingClient);

		return new ReactiveDiscoveryClientHealthIndicator(reactiveClient, properties);
	}

	/**
	 * Post an event so that health indicator is initialized.
	 */
	@Bean
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	KubernetesDiscoveryClientHealthIndicatorInitializer reactiveIndicatorInitializer(
			ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {
		LOG.debug(() -> "Will publish InstanceRegisteredEvent from reactive implementation");
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

}
