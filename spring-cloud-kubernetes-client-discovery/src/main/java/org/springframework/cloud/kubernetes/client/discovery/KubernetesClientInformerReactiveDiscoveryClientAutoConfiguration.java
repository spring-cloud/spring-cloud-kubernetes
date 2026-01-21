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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveDisabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
public final class KubernetesClientInformerReactiveDiscoveryClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveDisabled
	KubernetesClientInformerReactiveDiscoveryClient kubernetesClientInformerReactiveDiscoveryClient(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedIndexInformer<V1Service>> serviceInformers,
			List<SharedIndexInformer<V1Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties,
			CoreV1Api coreV1Api, Predicate<V1Service> predicate) {

		KubernetesClientInformerDiscoveryClient blockingClient = new KubernetesClientInformerDiscoveryClient(
				sharedInformerFactories, serviceListers, endpointsListers, serviceInformers, endpointsInformers,
				properties, coreV1Api, predicate);
		blockingClient.afterPropertiesSet();

		return new KubernetesClientInformerReactiveDiscoveryClient(blockingClient);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveEnabled
	KubernetesClientCacheableInformerReactiveDiscoveryClient kubernetesClientCacheableInformerReactiveDiscoveryClient(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedIndexInformer<V1Service>> serviceInformers,
			List<SharedIndexInformer<V1Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties,
			CoreV1Api coreV1Api, Predicate<V1Service> predicate) {

		KubernetesClientInformerDiscoveryClient blockingClient = new KubernetesClientInformerDiscoveryClient(
				sharedInformerFactories, serviceListers, endpointsListers, serviceInformers, endpointsInformers,
				properties, coreV1Api, predicate);
		blockingClient.afterPropertiesSet();

		return new KubernetesClientCacheableInformerReactiveDiscoveryClient(blockingClient);
	}

}
