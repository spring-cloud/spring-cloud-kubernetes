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

package org.springframework.cloud.kubernetes.discovery;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesReactiveDiscovery
@EnableConfigurationProperties({ DiscoveryClientHealthIndicatorProperties.class, KubernetesDiscoveryProperties.class })
@AutoConfigureAfter(KubernetesDiscoveryClientReactiveAutoConfiguration.class)
public class KubernetesDiscoveryClientHealthAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	PodUtils<?> kubernetesDiscoveryPodUtils() {
		return new KubernetesDiscoveryPodUtils();
	}

	@Bean
	@ConditionalOnMissingBean
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

	/**
	 * Post an event so that health indicator is initialized.
	 */
	@Bean
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	KubernetesDiscoveryClientHealthIndicatorInitializer reactiveIndicatorInitializer(
			ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

	/**
	 * unlike the blocking implementation, we need to register the health indicator.
	 */
	@Bean
	@ConditionalOnBean(KubernetesReactiveDiscoveryClient.class)
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties) {
		return new ReactiveDiscoveryClientHealthIndicator(client, properties);
	}

	/**
	 * unlike the blocking implementation, we need to register the health indicator.
	 */
	@Bean
	@ConditionalOnMissingBean(KubernetesReactiveDiscoveryClient.class)
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator reactiveDiscoveryClientHealthIndicator(WebClient.Builder webClientBuilder,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			DiscoveryClientHealthIndicatorProperties properties) {

		KubernetesReactiveDiscoveryClient reactiveClient = new KubernetesReactiveDiscoveryClient(webClientBuilder,
				kubernetesDiscoveryProperties);

		return new ReactiveDiscoveryClientHealthIndicator(reactiveClient, properties);
	}

}
