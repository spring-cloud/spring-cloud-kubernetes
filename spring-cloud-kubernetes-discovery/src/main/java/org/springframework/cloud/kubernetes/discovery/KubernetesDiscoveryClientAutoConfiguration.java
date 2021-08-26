/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnProperty(value = { "spring.cloud.kubernetes.enabled", "spring.cloud.kubernetes.discovery.enabled" },
		matchIfMissing = true)
@EnableConfigurationProperties(KubernetesDiscoveryClientProperties.class)
public class KubernetesDiscoveryClientAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	public static class Servlet {

		@Bean
		@ConditionalOnMissingClass("org.springframework.web.reactive.function.client.WebClient")
		public RestTemplate restTemplate() {
			return new RestTemplateBuilder().build();
		}

		@Bean
		@ConditionalOnMissingClass("org.springframework.web.reactive.function.client.WebClient")
		public DiscoveryClient kubernetesDiscoveryClient(RestTemplate restTemplate,
				KubernetesDiscoveryClientProperties properties) {
			return new KubernetesDiscoveryClient(restTemplate, properties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnReactiveDiscoveryEnabled
	public static class Reactive {

		@Bean
		@ConditionalOnClass(name = { "org.springframework.web.reactive.function.client.WebClient" })
		@ConditionalOnMissingBean(WebClient.Builder.class)
		public WebClient.Builder webClientBuilder() {
			return WebClient.builder();
		}

		@Bean
		@ConditionalOnClass(name = { "org.springframework.web.reactive.function.client.WebClient" })
		public ReactiveDiscoveryClient kubernetesReactiveDiscoveryClient(WebClient.Builder webClientBuilder,
				KubernetesDiscoveryClientProperties properties) {
			return new KubernetesReactiveDiscoveryClient(webClientBuilder, properties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableCaching
	public class CachingConfig {

		@Bean
		@ConditionalOnMissingBean
		public CacheManager cacheManager() {
			SimpleCacheManager cacheManager = new SimpleCacheManager();
			cacheManager.setCaches(
					Arrays.asList(new ConcurrentMapCache("serviceinstances"), new ConcurrentMapCache("services")));
			return cacheManager;
		}

	}

}
