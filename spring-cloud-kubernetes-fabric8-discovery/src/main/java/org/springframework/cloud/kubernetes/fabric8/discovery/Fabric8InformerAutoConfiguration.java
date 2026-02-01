/*
 * Copyright 2012-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import jakarta.annotation.Nullable;

import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnBlockingOrReactiveDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.conditionals.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.getApplicationNamespace;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@ConditionalOnBlockingOrReactiveDiscoveryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ Fabric8AutoConfiguration.class })
final class Fabric8InformerAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
		LogFactory.getLog(Fabric8InformerAutoConfiguration.class));

	// we rely on the order of namespaces to enable listers, as such provide a bean of
	// namespaces as a list, instead of the incoming Set.
	@Bean
	List<String> fabric8InformerNamespaces(KubernetesDiscoveryProperties properties,
			KubernetesClient kubernetesClient, Environment environment) {

		KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(environment);

		if (!properties.namespaces().isEmpty()) {
			LOG.debug(() -> "discovering endpoints in namespaces : " + properties.namespaces());
			return properties.namespaces().stream().toList();
		}

		if (properties.allNamespaces()) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
			return List.of("");
		}

		String namespace = getApplicationNamespace(kubernetesClient, null, "fabric8 discovery", namespaceProvider);
		LOG.debug(() -> "discovering endpoints in namespace : " + namespace);
		return List.of(namespace);
	}

	@Bean
	@ConditionalOnMissingBean(value = SharedInformerFactory.class, parameterizedContainer = List.class)
	List<SharedInformerFactory> sharedInformerFactories(ApiClient apiClient, List<String> selectiveNamespaces) {

		new SharedInformerFactory()

		int howManyNamespaces = selectiveNamespaces.size();
		List<SharedInformerFactory> sharedInformerFactories = new ArrayList<>(howManyNamespaces);
		for (int i = 0; i < howManyNamespaces; ++i) {
			sharedInformerFactories.add(new SharedInformerFactory(apiClient));
		}
		return sharedInformerFactories;
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Service.class,
		parameterizedContainer = { List.class, SharedIndexInformer.class })
	List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers(
		List<SharedInformerFactory> sharedInformerFactories, List<String> selectiveNamespaces, CoreV1Api api,
		KubernetesDiscoveryProperties properties) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<SharedIndexInformer<V1Service>> serviceSharedIndexedInformers = new ArrayList<>(howManyNamespaces);
		for (int i = 0; i < howManyNamespaces; ++i) {
			String namespace = selectiveNamespaces.get(i);

			CallGenerator callGenerator = servicesCallGenerator(api, properties.serviceLabels(), namespace);

			SharedIndexInformer<V1Service> sharedIndexInformer = sharedInformerFactories.get(i)
				.sharedIndexInformerFor(callGenerator, V1Service.class, V1ServiceList.class);
			serviceSharedIndexedInformers.add(sharedIndexInformer);
		}
		return serviceSharedIndexedInformers;
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Service.class, parameterizedContainer = { List.class, Lister.class })
	List<Lister<V1Service>> serviceListers(List<String> selectiveNamespaces,
		List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<Lister<V1Service>> serviceListers = new ArrayList<>(howManyNamespaces);

		for (int i = 0; i < howManyNamespaces; ++i) {
			String namespace = selectiveNamespaces.get(i);
			Lister<V1Service> lister = new Lister<>(serviceSharedIndexInformers.get(i).getIndexer(), namespace);
			LOG.debug(() -> "registering lister (for services) in namespace : " + namespace);
			serviceListers.add(lister);
		}

		return serviceListers;
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Endpoints.class,
		parameterizedContainer = { List.class, SharedIndexInformer.class })
	List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers(
		List<SharedInformerFactory> sharedInformerFactories, List<String> selectiveNamespaces, CoreV1Api api,
		KubernetesDiscoveryProperties properties) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexedInformers = new ArrayList<>(howManyNamespaces);
		for (int i = 0; i < howManyNamespaces; ++i) {
			String namespace = selectiveNamespaces.get(i);

			CallGenerator callGenerator = endpointsCallGenerator(api, properties.serviceLabels(), namespace);

			SharedIndexInformer<V1Endpoints> sharedIndexInformer = sharedInformerFactories.get(i)
				.sharedIndexInformerFor(callGenerator, V1Endpoints.class, V1EndpointsList.class);
			endpointsSharedIndexedInformers.add(sharedIndexInformer);
		}
		return endpointsSharedIndexedInformers;
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Endpoints.class, parameterizedContainer = { List.class, Lister.class })
	List<Lister<V1Endpoints>> endpointsListers(List<String> selectiveNamespaces,
		List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<Lister<V1Endpoints>> endpointsListers = new ArrayList<>(howManyNamespaces);

		for (int i = 0; i < howManyNamespaces; ++i) {
			String namespace = selectiveNamespaces.get(i);
			Lister<V1Endpoints> lister = new Lister<>(endpointsSharedIndexInformers.get(i).getIndexer(), namespace);
			LOG.debug(() -> "registering lister (for endpoints) in namespace : " + namespace);
			endpointsListers.add(lister);
		}

		return endpointsListers;
	}


}
