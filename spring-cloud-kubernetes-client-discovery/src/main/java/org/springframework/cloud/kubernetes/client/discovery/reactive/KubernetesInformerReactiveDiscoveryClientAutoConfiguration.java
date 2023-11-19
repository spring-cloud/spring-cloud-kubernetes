/*
 * Copyright 2019-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery.reactive;

import java.util.List;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientPodUtils;
import org.springframework.cloud.kubernetes.client.discovery.ConditionalOnSelectiveNamespacesMissing;
import org.springframework.cloud.kubernetes.client.discovery.ConditionalOnSelectiveNamespacesPresent;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerSelectiveNamespacesAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer.RegisteredEventSource;

/**
 * @author Ryan Baxter
 */

@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesReactiveDiscovery
@AutoConfigureBefore({ SimpleReactiveDiscoveryClientAutoConfiguration.class,
		ReactiveCommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ ReactiveCompositeDiscoveryClientAutoConfiguration.class,
		KubernetesDiscoveryPropertiesAutoConfiguration.class, KubernetesClientInformerAutoConfiguration.class,
		KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class })
public class KubernetesInformerReactiveDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesInformerReactiveDiscoveryClientAutoConfiguration.class));

	@Deprecated(forRemoval = true)
	public ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesInformerReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties,
			KubernetesClientPodUtils podUtils) {
		ReactiveDiscoveryClientHealthIndicator healthIndicator = new ReactiveDiscoveryClientHealthIndicator(client,
				properties);
		InstanceRegisteredEvent<RegisteredEventSource> event = new InstanceRegisteredEvent<>(
				new RegisteredEventSource("kubernetes", podUtils.isInsideKubernetes(), podUtils.currentPod().get()),
				null);
		healthIndicator.onApplicationEvent(event);
		return healthIndicator;
	}

	@Deprecated(forRemoval = true)
	public KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(
			KubernetesNamespaceProvider kubernetesNamespaceProvider, SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerReactiveDiscoveryClient(kubernetesNamespaceProvider, sharedInformerFactory,
				serviceLister, endpointsLister, serviceInformer, endpointsInformer, properties);
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
			KubernetesInformerReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(client, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	KubernetesInformerReactiveDiscoveryClient kubernetesClientReactiveDiscoveryClient(
			KubernetesInformerDiscoveryClient kubernetesClientInformerDiscoveryClient) {
		return new KubernetesInformerReactiveDiscoveryClient(kubernetesClientInformerDiscoveryClient);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesMissing.class)
	KubernetesInformerDiscoveryClient kubernetesClientInformerDiscoveryClient(
			SharedInformerFactory sharedInformerFactory, Lister<V1Service> serviceLister,
			Lister<V1Endpoints> endpointsLister, SharedInformer<V1Service> serviceInformer,
			SharedInformer<V1Endpoints> endpointsInformer, KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerDiscoveryClient(sharedInformerFactory, serviceLister, endpointsLister,
				serviceInformer, endpointsInformer, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesPresent.class)
	KubernetesInformerDiscoveryClient selectiveNamespacesKubernetesClientInformerDiscoveryClient(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedInformer<V1Service>> serviceInformers,
			List<SharedInformer<V1Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerDiscoveryClient(sharedInformerFactories, serviceListers, endpointsListers,
				serviceInformers, endpointsInformers, properties);
	}

}
