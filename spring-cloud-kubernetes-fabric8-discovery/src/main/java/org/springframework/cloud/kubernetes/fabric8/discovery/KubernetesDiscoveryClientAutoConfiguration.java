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
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Auto configuration for discovery clients.
 *
 * @author Mauricio Salatino
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ Fabric8AutoConfiguration.class })
@Import(KubernetesDiscoveryClientHealthIndicatorConfiguration.class)
@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
public class KubernetesDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesClientServicesFunction.class));

	@Bean
	public KubernetesClientServicesFunction servicesFunction(KubernetesDiscoveryProperties properties,
			Environment environment) {

		if (properties.allNamespaces()) {
			LOG.debug(() -> "searching for services in namespaces : " + properties.namespaces());
			return (client) -> client.services().inAnyNamespace().withLabels(properties.serviceLabels());
		}

		return client -> {
			String namespace = Fabric8Utils.getApplicationNamespace(client, null, "discovery-service",
					new KubernetesNamespaceProvider(environment));
			LOG.debug(() -> "searching for services in namespace : " + namespace);
			return client.services().inNamespace(namespace).withLabels(properties.serviceLabels());
		};
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
			return new KubernetesDiscoveryClient(client, properties, kubernetesClientServicesFunction, null,
					new ServicePortSecureResolver(properties));
		}

	}

}
