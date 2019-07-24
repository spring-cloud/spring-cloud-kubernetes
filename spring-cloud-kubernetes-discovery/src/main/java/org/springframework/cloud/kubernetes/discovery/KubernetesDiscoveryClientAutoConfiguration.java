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

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.registry.KubernetesRegistration;
import org.springframework.cloud.kubernetes.registry.KubernetesServiceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration for discovery clients.
 *
 * @author Mauricio Salatino
 * @author Tim Ysewyn
 */
@Configuration
@ConditionalOnDiscoveryEnabled
@ConditionalOnProperty(name = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class,
		CommonsClientAutoConfiguration.class })
public class KubernetesDiscoveryClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public DefaultIsServicePortSecureResolver isServicePortSecureResolver(
			KubernetesDiscoveryProperties properties) {
		return new DefaultIsServicePortSecureResolver(properties);
	}

	@Bean
	public KubernetesClientServicesFunction servicesFunction(
			KubernetesDiscoveryProperties properties) {
		if (properties.getServiceLabels().isEmpty()) {
			return KubernetesClient::services;
		}

		return (client) -> client.services().withLabels(properties.getServiceLabels());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.cloud.kubernetes.discovery.enabled",
			matchIfMissing = true)
	public KubernetesDiscoveryClient kubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties properties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction,
			DefaultIsServicePortSecureResolver isServicePortSecureResolver) {
		return new KubernetesDiscoveryClient(client, properties,
				kubernetesClientServicesFunction, isServicePortSecureResolver);
	}

	@Bean
	public KubernetesServiceRegistry getServiceRegistry() {
		return new KubernetesServiceRegistry();
	}

	@Bean
	public KubernetesRegistration getRegistration(KubernetesClient client,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesRegistration(client, properties);
	}

	@Bean
	public KubernetesDiscoveryProperties getKubernetesDiscoveryProperties() {
		return new KubernetesDiscoveryProperties();
	}

}
