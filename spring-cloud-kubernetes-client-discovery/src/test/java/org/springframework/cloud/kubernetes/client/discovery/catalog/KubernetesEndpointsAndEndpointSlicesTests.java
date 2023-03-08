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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EndpointAddressBuilder;
import io.kubernetes.client.openapi.models.V1EndpointBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSliceBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSliceList;
import io.kubernetes.client.openapi.models.V1EndpointSliceListBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSubsetBuilder;
import io.kubernetes.client.openapi.models.V1EndpointsBuilder;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1EndpointsListBuilder;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * make sure that all the tests for endpoints are also handled by endpoint slices
 *
 * @author wind57
 */
abstract class KubernetesEndpointsAndEndpointSlicesTests {

	static final KubernetesNamespaceProvider NAMESPACE_PROVIDER = Mockito.mock(KubernetesNamespaceProvider.class);

	static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
			.forClass(HeartbeatEvent.class);

	static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito.mock(ApplicationEventPublisher.class);

	/**
	 * test in all namespaces with service labels being empty
	 */
	abstract void testInAllNamespacesEmptyServiceLabels();

	/**
	 * test in all namespaces with service labels having a single label present
	 */
	abstract void testInAllNamespacesWithSingleLabel();

	/**
	 * test in all namespaces with service labels having two labels
	 */
	abstract void testInAllNamespacesWithDoubleLabel();

	/**
	 * test in some specific namespaces with service labels being empty
	 */
	abstract void testInSpecificNamespacesEmptyServiceLabels();

	/**
	 * test in some specific namespaces with service labels having a single label present
	 */
	abstract void testInSpecificNamespacesWithSingleLabel();

	/**
	 * test in some specific namespaces with service labels having two labels
	 */
	abstract void testInSpecificNamespacesWithDoubleLabel();

	/**
	 * test in one namespace with service labels being empty
	 */
	abstract void testInOneNamespaceEmptyServiceLabels();

	/**
	 * test in one namespace with service labels having a single label present
	 */
	abstract void testInOneNamespaceWithSingleLabel();

	/**
	 * test in one namespace with service labels having two labels
	 */
	abstract void testInOneNamespaceWithDoubleLabel();

	KubernetesCatalogWatch createWatcherInAllNamespacesWithLabels(Map<String, String> labels, Set<String> namespaces,
			CoreV1Api coreV1Api, ApiClient apiClient, boolean endpointSlices) {

		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60, false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(coreV1Api, apiClient, properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new KubernetesEndpointSlicesCatalogWatch()).when(watch).stateGenerator();
		}

		watch.postConstruct();
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}

	KubernetesCatalogWatch createWatcherInSpecificNamespacesWithLabels(Set<String> namespaces,
			Map<String, String> labels, CoreV1Api coreV1Api, ApiClient apiClient, boolean endpointSlices) {

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60, false, "", Set.of(), labels, "", null, 0, false);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(coreV1Api, apiClient, properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new KubernetesEndpointSlicesCatalogWatch()).when(watch).stateGenerator();
		}

		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		watch.postConstruct();
		return watch;

	}

	KubernetesCatalogWatch createWatcherInSpecificNamespaceWithLabels(String namespace, Map<String, String> labels,
			CoreV1Api coreV1Api, ApiClient apiClient, boolean endpointSlices) {

		when(NAMESPACE_PROVIDER.getNamespace()).thenReturn(namespace);

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60, false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(coreV1Api, apiClient, properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new KubernetesEndpointSlicesCatalogWatch()).when(watch).stateGenerator();
		}

		watch.postConstruct();
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}

	V1EndpointsList endpoints(String name, String namespace) {
		return new V1EndpointsListBuilder().addToItems(new V1EndpointsBuilder()
				.addToSubsets(new V1EndpointSubsetBuilder().addToAddresses(new V1EndpointAddressBuilder()
						.withTargetRef(new V1ObjectReferenceBuilder().withName(name).withNamespace(namespace).build())
						.build()).build())
				.build()).build();
	}

	V1EndpointSliceList endpointSlices(String name, String namespace) {
		return new V1EndpointSliceListBuilder()
				.addToItems(new V1EndpointSliceBuilder().addToEndpoints(new V1EndpointBuilder()
						.withTargetRef(new V1ObjectReferenceBuilder().withName(name).withNamespace(namespace).build())
						.build()).build())
				.build();
	}

	static void invokeAndAssert(KubernetesCatalogWatch watch, List<EndpointNameAndNamespace> state) {
		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		assertThat(event.getValue()).isEqualTo(state);
	}

}
