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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.health.DiscoveryClientHealthIndicatorProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveDisabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
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
	@ConditionalOnDiscoveryCacheableReactiveDisabled
	KubernetesReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(WebClient.Builder webClientBuilder,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesReactiveDiscoveryClient(webClientBuilder, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnDiscoveryCacheableReactiveEnabled
	KubernetesCacheableReactiveDiscoveryClient kubernetesCacheableReactiveDiscoveryClient(
			WebClient.Builder webClientBuilder, KubernetesDiscoveryProperties properties) {
		return new KubernetesCacheableReactiveDiscoveryClient(webClientBuilder, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

}
