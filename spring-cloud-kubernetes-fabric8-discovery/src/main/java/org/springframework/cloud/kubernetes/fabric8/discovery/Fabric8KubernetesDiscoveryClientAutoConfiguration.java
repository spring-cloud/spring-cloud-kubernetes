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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesBlockingDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesBlockingDiscoveryHealthInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthIndicatorInitializer;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

/**
 * Auto configuration for discovery clients.
 *
 * @author Mauricio Salatino
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesBlockingDiscovery
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ Fabric8AutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
		Fabric8DiscoveryClientPredicateAutoConfiguration.class })
class Fabric8KubernetesDiscoveryClientAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(Fabric8KubernetesDiscoveryClientAutoConfiguration.class));

	@Bean
	@ConditionalOnMissingBean
	Fabric8KubernetesDiscoveryClient kubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties properties, Predicate<Service> predicate, Environment environment) {
		ServicePortSecureResolver servicePortSecureResolver = new ServicePortSecureResolver(properties);
		KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);
		return new Fabric8KubernetesDiscoveryClient(client, properties, servicePortSecureResolver, namespaceProvider,
				predicate);
	}

	@Bean
	@ConditionalOnSpringCloudKubernetesBlockingDiscoveryHealthInitializer
	public KubernetesDiscoveryClientHealthIndicatorInitializer indicatorInitializer(
			ApplicationEventPublisher applicationEventPublisher, PodUtils<?> podUtils) {
		LOG.debug(() -> "Will publish InstanceRegisteredEvent from blocking implementation");
		return new KubernetesDiscoveryClientHealthIndicatorInitializer(podUtils, applicationEventPublisher);
	}

}
