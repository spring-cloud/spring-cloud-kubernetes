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

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientPodUtils;
import org.springframework.cloud.kubernetes.client.discovery.ConditionalOnSelectiveNamespacesDisabled;
import org.springframework.cloud.kubernetes.client.discovery.ConditionalOnSelectiveNamespacesEnabled;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerAutoConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerSelectiveNamespacesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author Ryan Baxter
 */

@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@ConditionalOnReactiveDiscoveryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureBefore({ SimpleReactiveDiscoveryClientAutoConfiguration.class,
		ReactiveCommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ ReactiveCompositeDiscoveryClientAutoConfiguration.class,
		KubernetesDiscoveryPropertiesAutoConfiguration.class, KubernetesInformerAutoConfiguration.class,
		KubernetesInformerSelectiveNamespacesAutoConfiguration.class })
public class KubernetesInformerReactiveDiscoveryClientAutoConfiguration {

	@Bean
	@ConditionalOnClass(name = "org.springframework.boot.actuate.health.ReactiveHealthIndicator")
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	public ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesInformerReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties,
			KubernetesClientPodUtils podUtils) {
		ReactiveDiscoveryClientHealthIndicator healthIndicator = new ReactiveDiscoveryClientHealthIndicator(client,
				properties);
		InstanceRegisteredEvent<?> event = new InstanceRegisteredEvent<>(podUtils.currentPod(), null);
		healthIndicator.onApplicationEvent(event);
		return healthIndicator;
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesDisabled.class)
	public KubernetesInformerReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(
			KubernetesNamespaceProvider kubernetesNamespaceProvider, SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerReactiveDiscoveryClient(kubernetesNamespaceProvider, sharedInformerFactory,
				serviceLister, endpointsLister, serviceInformer, endpointsInformer, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesEnabled.class)
	public KubernetesInformerReactiveDiscoveryClient selectiveNamespacesKubernetesReactiveDiscoveryClient(
		KubernetesNamespaceProvider kubernetesNamespaceProvider, List<SharedInformerFactory> sharedInformerFactories,
		List<Lister<V1Service>> serviceListers, List<Lister<V1Endpoints>> endpointsListers,
		List<SharedInformer<V1Service>> serviceInformers, List<SharedInformer<V1Endpoints>> endpointsInformers,
		KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerReactiveDiscoveryClient(kubernetesNamespaceProvider, sharedInformerFactories,
			serviceListers, endpointsListers, serviceInformers, endpointsInformers, properties);
	}

}
