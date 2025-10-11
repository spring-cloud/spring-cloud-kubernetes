/*
 * Copyright 2013-present the original author or authors.
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

import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveDisabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

/**
 * Auto configuration for reactive discovery client.
 *
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesReactiveDiscovery
@AutoConfigureBefore({ SimpleReactiveDiscoveryClientAutoConfiguration.class,
		ReactiveCommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ ReactiveCompositeDiscoveryClientAutoConfiguration.class,
		Fabric8DiscoveryClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
		Fabric8DiscoveryClientSpelAutoConfiguration.class })
final class Fabric8ReactiveDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8ReactiveDiscoveryClientAutoConfiguration.class));

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveDisabled
	Fabric8ReactiveDiscoveryClient fabric8ReactiveDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties properties, Predicate<Service> predicate, Environment environment) {
		ServicePortSecureResolver servicePortSecureResolver = new ServicePortSecureResolver(properties);
		KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);
		Fabric8DiscoveryClient fabric8DiscoveryClient = new Fabric8DiscoveryClient(client, properties,
				servicePortSecureResolver, namespaceProvider, predicate);
		return new Fabric8ReactiveDiscoveryClient(fabric8DiscoveryClient);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveEnabled
	Fabric8CacheableReactiveDiscoveryClient fabric8CacheableReactiveDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties properties, Predicate<Service> predicate, Environment environment) {
		ServicePortSecureResolver servicePortSecureResolver = new ServicePortSecureResolver(properties);
		KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);
		Fabric8DiscoveryClient fabric8DiscoveryClient = new Fabric8DiscoveryClient(client, properties,
				servicePortSecureResolver, namespaceProvider, predicate);
		return new Fabric8CacheableReactiveDiscoveryClient(fabric8DiscoveryClient);
	}

	/**
	 * Post an event so that health indicator is initialized.
	 */
	@Bean
	@ConditionalOnClass(name = "org.springframework.boot.health.contributor.ReactiveHealthIndicator")
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	KubernetesDiscoveryClientHealthIndicatorInitializer reactiveIndicatorInitializer(
			ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {
		LOG.debug(() -> "Will publish InstanceRegisteredEvent from reactive implementation");
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

	@Bean
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			Fabric8ReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(client, properties);
	}

}
