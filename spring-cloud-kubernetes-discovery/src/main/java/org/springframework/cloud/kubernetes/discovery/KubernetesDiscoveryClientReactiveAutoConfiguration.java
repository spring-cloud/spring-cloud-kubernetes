/*
 * Copyright 2013-2023 the original author or authors.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.client.discovery.health.reactive.ReactiveDiscoveryClientHealthIndicator;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesReactiveDiscovery
@EnableConfigurationProperties({ DiscoveryClientHealthIndicatorProperties.class, KubernetesDiscoveryProperties.class })
class KubernetesDiscoveryClientReactiveAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

	@Bean
	@ConditionalOnMissingBean
	KubernetesReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(WebClient.Builder webClientBuilder,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesReactiveDiscoveryClient(webClientBuilder, properties);
	}

	@Bean
	@ConditionalOnSpringCloudKubernetesReactiveDiscoveryHealthInitializer
	ReactiveDiscoveryClientHealthIndicator kubernetesReactiveDiscoveryClientHealthIndicator(
			KubernetesReactiveDiscoveryClient client, DiscoveryClientHealthIndicatorProperties properties,
			ApplicationContext applicationContext) {
		ReactiveDiscoveryClientHealthIndicator healthIndicator = new ReactiveDiscoveryClientHealthIndicator(client,
				properties);
		InstanceRegisteredEvent event = new InstanceRegisteredEvent(applicationContext.getId(), null);
		healthIndicator.onApplicationEvent(event);
		return healthIndicator;
	}

}
