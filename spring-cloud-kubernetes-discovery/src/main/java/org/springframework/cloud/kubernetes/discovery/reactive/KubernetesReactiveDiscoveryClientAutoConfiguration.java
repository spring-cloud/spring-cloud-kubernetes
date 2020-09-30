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

package org.springframework.cloud.kubernetes.discovery.reactive;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.discovery.KubernetesClientServicesFunction;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration for reactive discovery client.
 *
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnReactiveDiscoveryEnabled
@ConditionalOnKubernetesEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@AutoConfigureBefore({ SimpleReactiveDiscoveryClientAutoConfiguration.class,
		ReactiveCommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ ReactiveCompositeDiscoveryClientAutoConfiguration.class,
		KubernetesDiscoveryClientAutoConfiguration.class })
public class KubernetesReactiveDiscoveryClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public KubernetesReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties properties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction) {
		return new KubernetesReactiveDiscoveryClient(client, properties, kubernetesClientServicesFunction);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.boot.actuate.health.ReactiveHealthIndicator")
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	public ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(client, properties);
	}

}
