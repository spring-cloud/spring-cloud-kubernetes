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

package org.springframework.cloud.kubernetes.client.discovery;

import java.time.Duration;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.spring.extended.controller.KubernetesInformerFactoryProcessor;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;
import io.kubernetes.client.util.Namespaces;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.ResolvableType;

/**
 * @author Ryan Baxter
 */
class SpringCloudKubernetesInformerFactoryProcessor extends KubernetesInformerFactoryProcessor {

	private static final Logger log = LoggerFactory.getLogger(SpringCloudKubernetesInformerFactoryProcessor.class);

	private BeanDefinitionRegistry beanDefinitionRegistry;

	private final ApiClient apiClient;

	private final SharedInformerFactory sharedInformerFactory;

	private final KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	private final KubernetesNamespaceProvider kubernetesNamespaceProvider;

	@Autowired
	SpringCloudKubernetesInformerFactoryProcessor(KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesNamespaceProvider kubernetesNamespaceProvider, ApiClient apiClient,
			SharedInformerFactory sharedInformerFactory) {
		super();
		this.apiClient = apiClient;
		this.sharedInformerFactory = sharedInformerFactory;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
		this.kubernetesDiscoveryProperties = kubernetesDiscoveryProperties;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		String namespace = kubernetesDiscoveryProperties.isAllNamespaces() ? Namespaces.NAMESPACE_ALL
				: kubernetesNamespaceProvider.getNamespace() == null ? Namespaces.NAMESPACE_DEFAULT
						: kubernetesNamespaceProvider.getNamespace();

		this.apiClient.setHttpClient(this.apiClient.getHttpClient().newBuilder().readTimeout(Duration.ZERO).build());

		KubernetesInformers kubernetesInformers = sharedInformerFactory.getClass()
				.getAnnotation(KubernetesInformers.class);
		if (kubernetesInformers == null || kubernetesInformers.value().length == 0) {
			log.info("No informers registered in the sharedInformerFactory..");
			return;
		}
		for (KubernetesInformer kubernetesInformer : kubernetesInformers.value()) {
			final GenericKubernetesApi api = new GenericKubernetesApi(kubernetesInformer.apiTypeClass(),
					kubernetesInformer.apiListTypeClass(), kubernetesInformer.groupVersionResource().apiGroup(),
					kubernetesInformer.groupVersionResource().apiVersion(),
					kubernetesInformer.groupVersionResource().resourcePlural(), apiClient);
			SharedIndexInformer sharedIndexInformer = sharedInformerFactory.sharedIndexInformerFor(api,
					kubernetesInformer.apiTypeClass(), kubernetesInformer.resyncPeriodMillis(),
					kubernetesInformer.namespace().equals(Namespaces.NAMESPACE_ALL) ? namespace
							: kubernetesInformer.namespace());
			ResolvableType informerType = ResolvableType.forClassWithGenerics(SharedInformer.class,
					kubernetesInformer.apiTypeClass());
			RootBeanDefinition informerBean = new RootBeanDefinition();
			informerBean.setTargetType(informerType);
			informerBean.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
			informerBean.setAutowireCandidate(true);
			String informerBeanName = informerType.toString();
			this.beanDefinitionRegistry.registerBeanDefinition(informerBeanName, informerBean);
			beanFactory.registerSingleton(informerBeanName, sharedIndexInformer);

			Lister lister = new Lister(sharedIndexInformer.getIndexer());
			ResolvableType listerType = ResolvableType.forClassWithGenerics(Lister.class,
					kubernetesInformer.apiTypeClass());
			RootBeanDefinition listerBean = new RootBeanDefinition();
			listerBean.setTargetType(listerType);
			listerBean.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
			listerBean.setAutowireCandidate(true);
			String listerBeanName = listerType.toString();
			this.beanDefinitionRegistry.registerBeanDefinition(listerBeanName, listerBean);
			beanFactory.registerSingleton(listerBeanName, lister);
		}
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		this.beanDefinitionRegistry = registry;
	}

}
