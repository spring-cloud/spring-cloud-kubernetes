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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryHealthIndicatorEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration for discovery clients.
 *
 * @author Mauricio Salatino
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesEnabled
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ Fabric8AutoConfiguration.class })
public class KubernetesDiscoveryClientAutoConfiguration {

	@Bean
	public KubernetesClientServicesFunction servicesFunction(KubernetesDiscoveryProperties properties) {
		if (properties.getServiceLabels().isEmpty()) {
			if (properties.isAllNamespaces()) {
				return (client) -> client.services().inAnyNamespace();
			}
			else {
				return KubernetesClient::services;
			}
		}
		else {
			if (properties.isAllNamespaces()) {
				return (client) -> client.services().inAnyNamespace().withLabels(properties.getServiceLabels());
			}
			else {
				return (client) -> client.services().withLabels(properties.getServiceLabels());
			}
		}
	}

	@Bean
	public KubernetesDiscoveryProperties getKubernetesDiscoveryProperties() {
		return new KubernetesDiscoveryProperties();
	}

	@ConditionalOnClass({ HealthIndicator.class })
	@ConditionalOnDiscoveryEnabled
	@ConditionalOnDiscoveryHealthIndicatorEnabled
	@Configuration
	public static class KubernetesDiscoveryClientHealthIndicatorConfiguration {

		@Bean
		public KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(
				ApplicationEventPublisher applicationEventPublisher, PodUtils podUtils) {
			return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBlockingDiscoveryEnabled
	@ConditionalOnKubernetesDiscoveryEnabled
	public static class KubernetesDiscoveryClientConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public KubernetesDiscoveryClient kubernetesDiscoveryClient(KubernetesClient client,
				KubernetesDiscoveryProperties properties,
				KubernetesClientServicesFunction kubernetesClientServicesFunction) {
			return new KubernetesDiscoveryClient(client, properties, kubernetesClientServicesFunction,
					new ServicePortSecureResolver(properties));
		}

	}

}
