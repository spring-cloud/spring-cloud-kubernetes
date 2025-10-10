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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.function.Predicate;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnSpringCloudKubernetesBlockingDiscovery;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryClientHealthConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSpringCloudKubernetesBlockingDiscovery
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class,
		KubernetesClientInformerAutoConfiguration.class,
		KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class,
		KubernetesClientDiscoveryClientSpelAutoConfiguration.class })
@Import(KubernetesDiscoveryClientHealthConfiguration.class)
final class KubernetesClientInformerDiscoveryClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesMissing.class)
	KubernetesClientInformerDiscoveryClient kubernetesClientInformerDiscoveryClient(
			SharedInformerFactory sharedInformerFactory, Lister<V1Service> serviceLister,
			Lister<V1Endpoints> endpointsLister, SharedInformer<V1Service> serviceInformer,
			SharedInformer<V1Endpoints> endpointsInformer, KubernetesDiscoveryProperties properties,
			CoreV1Api coreV1Api, Predicate<V1Service> predicate) {
		return new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
				List.of(endpointsLister), List.of(serviceInformer), List.of(endpointsInformer), properties, coreV1Api,
				predicate);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesPresent.class)
	KubernetesClientInformerDiscoveryClient selectiveNamespacesKubernetesInformerDiscoveryClient(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedInformer<V1Service>> serviceInformers,
			List<SharedInformer<V1Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties,
			CoreV1Api coreV1Api, Predicate<V1Service> predicate) {
		return new KubernetesClientInformerDiscoveryClient(sharedInformerFactories, serviceListers, endpointsListers,
				serviceInformers, endpointsInformers, properties, coreV1Api, predicate);
	}

}
