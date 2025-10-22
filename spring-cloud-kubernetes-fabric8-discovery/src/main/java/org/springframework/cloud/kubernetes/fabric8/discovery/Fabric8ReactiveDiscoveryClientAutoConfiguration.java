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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ReactiveCommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveDisabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnDiscoveryCacheableReactiveEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnSpringCloudKubernetesReactiveDiscovery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
public final class Fabric8ReactiveDiscoveryClientAutoConfiguration {

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
		Fabric8DiscoveryClient blockingClient = new Fabric8DiscoveryClient(client, properties,
				servicePortSecureResolver, namespaceProvider, predicate);
		return new Fabric8CacheableReactiveDiscoveryClient(blockingClient);
	}

	// Above two beans are created when cacheable is enabled. In this case, we can't make
	// Fabric8DiscoveryClient a @Bean, since blocking discovery might be disabled and we
	// do
	// not want to allow wiring of it. Nevertheless, we still need an instance of
	// Fabric8DiscoveryClient
	// in order to create the ReactiveDiscoveryClientHealthIndicator and
	// Fabric8CacheableReactiveDiscoveryClient
	// As such, we create two of such instances in each bean.

}
