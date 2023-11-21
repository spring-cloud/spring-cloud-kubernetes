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

package org.springframework.cloud.kubernetes.fabric8.discovery.reactive;

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
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesClientServicesFunction;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesClientServicesFunctionProvider;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientAutoConfiguration;
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
		KubernetesDiscoveryClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class })
public class KubernetesReactiveDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesReactiveDiscoveryClientAutoConfiguration.class));

	@Bean
	@ConditionalOnMissingBean
	public KubernetesClientServicesFunction servicesFunction(KubernetesDiscoveryProperties properties,
			Environment environment) {
		return KubernetesClientServicesFunctionProvider.servicesFunction(properties, environment);
	}

	@Bean
	@ConditionalOnMissingBean
	public KubernetesReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties properties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction) {
		return new KubernetesReactiveDiscoveryClient(client, properties, kubernetesClientServicesFunction);
	}

	/**
	 * Post an event so that health indicator is initialized.
	 */
	@Bean
	@ConditionalOnClass(name = "org.springframework.boot.actuate.health.ReactiveHealthIndicator")
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	KubernetesDiscoveryClientHealthIndicatorInitializer reactiveIndicatorInitializer(
			ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {
		LOG.debug(() -> "Will publish InstanceRegisteredEvent from reactive implementation");
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

	@Bean
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	public ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(client, properties);
	}

}
