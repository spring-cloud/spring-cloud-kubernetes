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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
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
		KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class })
@Import(KubernetesDiscoveryClientHealthConfiguration.class)
public class KubernetesInformerDiscoveryClientAutoConfiguration {

	@Deprecated(forRemoval = true)
	public KubernetesInformerDiscoveryClient kubernetesInformerDiscoveryClient(
			KubernetesNamespaceProvider kubernetesNamespaceProvider, SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerDiscoveryClient(kubernetesNamespaceProvider.getNamespace(), sharedInformerFactory,
				serviceLister, endpointsLister, serviceInformer, endpointsInformer, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesMissing.class)
	KubernetesInformerDiscoveryClient kubernetesClientInformerDiscoveryClient(
			SharedInformerFactory sharedInformerFactory, Lister<V1Service> serviceLister,
			Lister<V1Endpoints> endpointsLister, SharedInformer<V1Service> serviceInformer,
			SharedInformer<V1Endpoints> endpointsInformer, KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerDiscoveryClient(sharedInformerFactory, serviceLister, endpointsLister,
				serviceInformer, endpointsInformer, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(ConditionalOnSelectiveNamespacesPresent.class)
	KubernetesInformerDiscoveryClient selectiveNamespacesKubernetesInformerDiscoveryClient(
			List<SharedInformerFactory> sharedInformerFactories, List<Lister<V1Service>> serviceListers,
			List<Lister<V1Endpoints>> endpointsListers, List<SharedInformer<V1Service>> serviceInformers,
			List<SharedInformer<V1Endpoints>> endpointsInformers, KubernetesDiscoveryProperties properties) {
		return new KubernetesInformerDiscoveryClient(sharedInformerFactories, serviceListers, endpointsListers,
				serviceInformers, endpointsInformers, properties);
	}

}
