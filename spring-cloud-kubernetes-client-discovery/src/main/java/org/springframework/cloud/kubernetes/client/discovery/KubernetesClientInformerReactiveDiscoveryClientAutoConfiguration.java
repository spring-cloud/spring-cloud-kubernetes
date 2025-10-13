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

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
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
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableBlockingDisabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveDisabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

/**
 * @author Ryan Baxter
 */

@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesReactiveDiscovery
@AutoConfigureBefore({ SimpleReactiveDiscoveryClientAutoConfiguration.class,
		ReactiveCommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ ReactiveCompositeDiscoveryClientAutoConfiguration.class,
		KubernetesDiscoveryPropertiesAutoConfiguration.class,
		KubernetesClientDiscoveryClientSpelAutoConfiguration.class })
final class KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration.class));

	// in case blocking is disabled
	@Bean
	@ConditionalOnMissingBean
	KubernetesClientInformerDiscoveryClient kubernetesClientInformerDiscoveryClientForReactiveImplementation(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedInformer<V1Service>> serviceInformers,
			List<SharedInformer<V1Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties,
			CoreV1Api coreV1Api, Predicate<V1Service> predicate) {

		return new KubernetesClientInformerDiscoveryClient(sharedInformerFactories, serviceListers, endpointsListers,
				serviceInformers, endpointsInformers, properties, coreV1Api, predicate);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveDisabled
	KubernetesClientInformerReactiveDiscoveryClient kubernetesClientReactiveDiscoveryClient(
			KubernetesClientInformerDiscoveryClient kubernetesClientInformerDiscoveryClient) {
		return new KubernetesClientInformerReactiveDiscoveryClient(kubernetesClientInformerDiscoveryClient);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveEnabled
	KubernetesClientInformerReactiveDiscoveryClient kubernetesClientCacheableReactiveDiscoveryClient(
		KubernetesClientInformerDiscoveryClient kubernetesClientInformerDiscoveryClient) {
		return new KubernetesClientInformerReactiveDiscoveryClient(kubernetesClientInformerDiscoveryClient);
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

	/**
	 * unlike the blocking implementation, we need to register the health indicator.
	 */
	@Bean
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesClientInformerReactiveDiscoveryClient client,
			DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(client, properties);
	}

}
