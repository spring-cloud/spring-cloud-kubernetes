/*
 * Copyright 2013-2022 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oleg Vyukov
 */
@SuppressWarnings({ "unchecked" })
class KubernetesCatalogWatchTest {

	private static final KubernetesClient CLIENT = Mockito.mock(KubernetesClient.class);

	private final KubernetesNamespaceProvider namespaceProvider = Mockito.mock(KubernetesNamespaceProvider.class);

	private KubernetesCatalogWatch kubernetesCatalogWatch;

	private static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito
			.mock(ApplicationEventPublisher.class);

	private static final MixedOperation<Endpoints, EndpointsList, Resource<Endpoints>> MIXED_OPERATION = Mockito
			.mock(MixedOperation.class);

	private static final NonNamespaceOperation<Endpoints, EndpointsList, Resource<Endpoints>> NON_NAMESPACE_OPERATION = Mockito
			.mock(NonNamespaceOperation.class);

	private static final FilterWatchListDeletable<Endpoints, EndpointsList> FILTER_WATCH_LIST_DELETABLE = Mockito
			.mock(FilterWatchListDeletable.class);

	private static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
			.forClass(HeartbeatEvent.class);

	@AfterEach
	void afterEach() {
		Mockito.reset(APPLICATION_EVENT_PUBLISHER, CLIENT, MIXED_OPERATION, FILTER_WATCH_LIST_DELETABLE,
				NON_NAMESPACE_OPERATION);
	}

	@Test
	void testRandomOrderChangePods() {

		createInSpecificNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createSingleEndpointEndpointListByPodName("api-pod", "other-pod"))
				.thenReturn(createSingleEndpointEndpointListByPodName("other-pod", "api-pod"));

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testRandomOrderChangePodsAllNamespaces() {

		createInAllNamespaceWatcher();

		when(MIXED_OPERATION.list()).thenReturn(createSingleEndpointEndpointListByPodName("api-pod", "other-pod"))
				.thenReturn(createSingleEndpointEndpointListByPodName("other-pod", "api-pod"));

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testRandomOrderChangeServices() {

		createInSpecificNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createEndpointsListByServiceName("api-service", "other-service"))
				.thenReturn(createEndpointsListByServiceName("other-service", "api-service"));

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testRandomOrderChangeServicesAllNamespaces() {

		createInAllNamespaceWatcher();

		when(MIXED_OPERATION.list()).thenReturn(createEndpointsListByServiceName("api-service", "other-service"))
				.thenReturn(createEndpointsListByServiceName("other-service", "api-service"));

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEventBody() {

		createInSpecificNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createSingleEndpointListWithNamespace("default", "api-pod", "other-pod"));

		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("api-pod", "default"),
				new EndpointNameAndNamespace("other-pod", "default"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	@Test
	void testEventBodyAllNamespaces() {

		createInAllNamespaceWatcher();

		when(MIXED_OPERATION.list())
				.thenReturn(createSingleEndpointListWithNamespace("default", "api-pod", "other-pod"));

		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("api-pod", "default"),
				new EndpointNameAndNamespace("other-pod", "default"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	@Test
	void testEndpointsWithoutSubsets() {

		createInSpecificNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListWithoutSubsets();

		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutSubsetsAllNamespaces() {

		createInAllNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListWithoutSubsets();

		when(MIXED_OPERATION.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutAddresses() {

		createInSpecificNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).setAddresses(null);

		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutAddressesAllNamespaces() {

		createInAllNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).setAddresses(null);

		when(MIXED_OPERATION.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutTargetRefs() {

		createInSpecificNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).getAddresses().get(0).setTargetRef(null);

		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutTargetRefsAllNamespaces() {

		createInAllNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("api-pod");
		endpoints.getItems().get(0).getSubsets().get(0).getAddresses().get(0).setTargetRef(null);

		when(MIXED_OPERATION.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	private EndpointsList createEndpointsListByServiceName(String... serviceNames) {
		List<Endpoints> endpoints = stream(serviceNames).map(s -> createEndpointsByPodName(s + "-singlePodUniqueId"))
				.collect(Collectors.toList());

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(endpoints);
		return endpointsList;
	}

	private EndpointsList createSingleEndpointEndpointListWithoutSubsets() {
		Endpoints endpoints = new Endpoints();

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private EndpointsList createSingleEndpointEndpointListByPodName(String... podNames) {
		Endpoints endpoints = new Endpoints();
		endpoints.setSubsets(createSubsetsByPodName(podNames));

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private EndpointsList createSingleEndpointListWithNamespace(String namespace, String... podNames) {
		Endpoints endpoints = new Endpoints();
		endpoints.setSubsets(createSubsetsWithNamespace(namespace, podNames));

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private Endpoints createEndpointsByPodName(String podName) {
		Endpoints endpoints = new Endpoints();
		endpoints.setSubsets(createSubsetsByPodName(podName));
		return endpoints;
	}

	private List<EndpointSubset> createSubsetsByPodName(String... names) {
		EndpointSubset endpointSubset = new EndpointSubset();
		endpointSubset.setAddresses(createEndpointAddressByPodNames(names));
		return Collections.singletonList(endpointSubset);
	}

	private List<EndpointSubset> createSubsetsWithNamespace(String namespace, String... names) {
		EndpointSubset endpointSubset = new EndpointSubset();
		endpointSubset.setAddresses(createEndpointAddressWithNamespace(names, namespace));
		return Collections.singletonList(endpointSubset);
	}

	private List<EndpointAddress> createEndpointAddressByPodNames(String[] names) {
		return stream(names).map(name -> {
			ObjectReference podRef = new ObjectReference();
			podRef.setName(name);
			EndpointAddress endpointAddress = new EndpointAddress();
			endpointAddress.setTargetRef(podRef);
			return endpointAddress;
		}).toList();
	}

	private List<EndpointAddress> createEndpointAddressWithNamespace(String[] names, String namespace) {
		return stream(names).map(name -> {
			ObjectReference podRef = new ObjectReference();
			podRef.setName(name);
			podRef.setNamespace(namespace);
			EndpointAddress endpointAddress = new EndpointAddress();
			endpointAddress.setTargetRef(podRef);
			return endpointAddress;
		}).toList();
	}

	private void createInAllNamespaceWatcher() {

		when(CLIENT.endpoints()).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.inAnyNamespace()).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.withLabels(Map.of())).thenReturn(MIXED_OPERATION);

		// all-namespaces = true
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, true, 60, false, "",
				Set.of(), Map.of(), "", null, 0);

		kubernetesCatalogWatch = new KubernetesCatalogWatch(CLIENT, properties, namespaceProvider);
		kubernetesCatalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
	}

	private void createInSpecificNamespaceWatcher() {

		// all-namespaces = false
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, true, 60, false, "",
				Set.of(), Map.of(), "", null, 0);

		kubernetesCatalogWatch = new KubernetesCatalogWatch(CLIENT, properties, namespaceProvider);
		kubernetesCatalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);

		when(namespaceProvider.getNamespace()).thenReturn("catalog-watcher-namespace");
		when(CLIENT.endpoints()).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.inNamespace("catalog-watcher-namespace")).thenReturn(NON_NAMESPACE_OPERATION);
		when(NON_NAMESPACE_OPERATION.withLabels(Map.of())).thenReturn(FILTER_WATCH_LIST_DELETABLE);
	}

}
