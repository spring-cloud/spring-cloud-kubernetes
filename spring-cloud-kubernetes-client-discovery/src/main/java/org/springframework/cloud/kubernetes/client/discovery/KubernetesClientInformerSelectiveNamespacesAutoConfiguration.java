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

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

/**
 * Auto-configuration to be used when "spring.cloud.kubernetes.discovery.namespaces" is
 * defined.
 *
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnKubernetesDiscoveryEnabled
@ConditionalOnBlockingOrReactiveEnabled
@Conditional(ConditionalOnSelectiveNamespacesPresent.class)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class, KubernetesDiscoveryPropertiesAutoConfiguration.class })
public class KubernetesClientInformerSelectiveNamespacesAutoConfiguration {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class));

	// we rely on the order of namespaces to enable listers, as such provide a bean of
	// namespaces
	// as a list, instead of the incoming Set.
	@Bean
	@ConditionalOnMissingBean
	public List<String> selectiveNamespaces(KubernetesDiscoveryProperties properties) {
		List<String> selectiveNamespaces = properties.namespaces().stream().sorted().toList();
		LOG.debug(() -> "using selective namespaces : " + selectiveNamespaces);
		return selectiveNamespaces;
	}

	@Bean
	@ConditionalOnMissingBean(value = SharedInformerFactory.class, parameterizedContainer = List.class)
	public List<SharedInformerFactory> sharedInformerFactories(ApiClient apiClient, List<String> selectiveNamespaces) {

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
	public List<SharedIndexInformer<V1Service>> serviceSharedIndexInformers(
			List<SharedInformerFactory> sharedInformerFactories, List<String> selectiveNamespaces,
			ApiClient apiClient) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<SharedIndexInformer<V1Service>> serviceSharedIndexedInformers = new ArrayList<>(howManyNamespaces);
		for (int i = 0; i < howManyNamespaces; ++i) {
			GenericKubernetesApi<V1Service, V1ServiceList> servicesApi = new GenericKubernetesApi<>(V1Service.class,
					V1ServiceList.class, "", "v1", "services", apiClient);
			SharedIndexInformer<V1Service> sharedIndexInformer = sharedInformerFactories.get(i)
					.sharedIndexInformerFor(servicesApi, V1Service.class, 0L, selectiveNamespaces.get(i));
			serviceSharedIndexedInformers.add(sharedIndexInformer);
		}
		return serviceSharedIndexedInformers;
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Service.class, parameterizedContainer = { List.class, Lister.class })
	public List<Lister<V1Service>> serviceListers(List<String> selectiveNamespaces,
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
	public List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexInformers(
			List<SharedInformerFactory> sharedInformerFactories, List<String> selectiveNamespaces,
			ApiClient apiClient) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<SharedIndexInformer<V1Endpoints>> endpointsSharedIndexedInformers = new ArrayList<>(howManyNamespaces);
		for (int i = 0; i < howManyNamespaces; ++i) {
			GenericKubernetesApi<V1Endpoints, V1EndpointsList> endpointsApi = new GenericKubernetesApi<>(
					V1Endpoints.class, V1EndpointsList.class, "", "v1", "endpoints", apiClient);
			SharedIndexInformer<V1Endpoints> sharedIndexInformer = sharedInformerFactories.get(i)
					.sharedIndexInformerFor(endpointsApi, V1Endpoints.class, 0L, selectiveNamespaces.get(i));
			endpointsSharedIndexedInformers.add(sharedIndexInformer);
		}
		return endpointsSharedIndexedInformers;
	}

	@Bean
	@ConditionalOnMissingBean(value = V1Endpoints.class, parameterizedContainer = { List.class, Lister.class })
	public List<Lister<V1Endpoints>> endpointsListers(List<String> selectiveNamespaces,
			List<SharedIndexInformer<V1Endpoints>> serviceSharedIndexInformers) {

		int howManyNamespaces = selectiveNamespaces.size();
		List<Lister<V1Endpoints>> endpointsListers = new ArrayList<>(howManyNamespaces);

		for (int i = 0; i < howManyNamespaces; ++i) {
			String namespace = selectiveNamespaces.get(i);
			Lister<V1Endpoints> lister = new Lister<>(serviceSharedIndexInformers.get(i).getIndexer());
			LOG.debug(() -> "registering lister (for endpoints) in namespace : " + namespace);
			endpointsListers.add(lister);
		}

		return endpointsListers;
	}

}
