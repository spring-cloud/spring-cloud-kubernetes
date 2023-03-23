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
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterNested;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
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

	private static final FilterWatchListDeletable<Endpoints, EndpointsList, Resource<Endpoints>> FILTER_WATCH_LIST_DELETABLE = Mockito
			.mock(FilterWatchListDeletable.class);

	private static final FilterNested<FilterWatchListDeletable<Endpoints, EndpointsList, Resource<Endpoints>>> FILTER_NESTED = Mockito
			.mock(FilterNested.class);

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
				.thenReturn(createSingleEndpointEndpointListByPodName("test", "api-pod", "other-pod"))
				.thenReturn(createSingleEndpointEndpointListByPodName("test", "other-pod", "api-pod"));
		mockServicesCall("api-pod", "test");
		mockServicesCall("other-pod", "test");

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testRandomOrderChangePodsAllNamespaces() {

		createInAllNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createSingleEndpointEndpointListByPodName("test", "api-pod", "other-pod"))
				.thenReturn(createSingleEndpointEndpointListByPodName("test", "other-pod", "api-pod"));

		mockServicesCall("api-pod", "test");
		mockServicesCall("other-pod", "test");

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testRandomOrderChangeServices() {

		createInSpecificNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createEndpointsListByServiceName("test", "api-service", "other-service"))
				.thenReturn(createEndpointsListByServiceName("test", "other-service", "api-service"));
		mockServicesCall("api-service", "test");
		mockServicesCall("other-service", "test");

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testRandomOrderChangeServicesAllNamespaces() {

		createInAllNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createEndpointsListByServiceName("test", "api-service", "other-service"))
				.thenReturn(createEndpointsListByServiceName("test", "other-service", "api-service"));
		mockServicesCall("api-service", "test");
		mockServicesCall("other-service", "test");

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEventBody() {

		createInSpecificNamespaceWatcher();

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createSingleEndpointListWithNamespace("default", "other-pod", "api-pod", "other-pod"));
		mockServicesCall("api-pod", "default");
		mockServicesCall("other-pod", "default");

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

		when(FILTER_WATCH_LIST_DELETABLE.list())
				.thenReturn(createSingleEndpointListWithNamespace("default", "other-pod", "api-pod", "other-pod"));
		mockServicesCall("api-pod", "default");
		mockServicesCall("other-pod", "default");

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

		EndpointsList endpoints = createSingleEndpointEndpointListWithoutSubsets("name", "test");
		mockServicesCall("name", "test");
		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutSubsetsAllNamespaces() {

		createInAllNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListWithoutSubsets("name", "test");
		mockServicesCall("name", "test");

		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutAddresses() {

		createInSpecificNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("test", "api-pod");
		mockServicesCall("api-pod", "test");
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

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("test", "api-pod");
		mockServicesCall("api-pod", "test");
		endpoints.getItems().get(0).getSubsets().get(0).setAddresses(null);

		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	@Test
	void testEndpointsWithoutTargetRefs() {

		createInSpecificNamespaceWatcher();

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("test", "api-pod");
		mockServicesCall("api-pod", "test");
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

		EndpointsList endpoints = createSingleEndpointEndpointListByPodName("test", "api-pod");
		mockServicesCall("api-pod", "test");
		endpoints.getItems().get(0).getSubsets().get(0).getAddresses().get(0).setTargetRef(null);

		when(FILTER_WATCH_LIST_DELETABLE.list()).thenReturn(endpoints);

		kubernetesCatalogWatch.catalogServicesWatch();
		// second execution on shuffleServices
		kubernetesCatalogWatch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(any(HeartbeatEvent.class));
	}

	private EndpointsList createEndpointsListByServiceName(String namespace, String... serviceNames) {
		List<Endpoints> endpoints = stream(serviceNames)
				.map(s -> createEndpointsByPodName(namespace, s + "-singlePodUniqueId")).collect(Collectors.toList());

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(endpoints);
		return endpointsList;
	}

	private EndpointsList createSingleEndpointEndpointListWithoutSubsets(String name, String namespace) {
		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withName(name).withNamespace(namespace)
				.endMetadata().build();

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private EndpointsList createSingleEndpointEndpointListByPodName(String namespace, String... podNames) {
		Endpoints endpoints = new Endpoints();
		endpoints.setSubsets(createSubsetsByPodName(podNames));
		endpoints.setMetadata(new ObjectMetaBuilder().withNamespace(namespace).build());

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private EndpointsList createSingleEndpointListWithNamespace(String namespace, String endpointsName,
			String... podNames) {
		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withNamespace(namespace).withName(endpointsName)
				.and().build();
		endpoints.setSubsets(createSubsetsWithNamespace(namespace, podNames));

		EndpointsList endpointsList = new EndpointsList();
		endpointsList.setItems(Collections.singletonList(endpoints));
		return endpointsList;
	}

	private Endpoints createEndpointsByPodName(String namespace, String podName) {
		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withNamespace(namespace).and().build();
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

		// all-namespaces = true
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, false);

		kubernetesCatalogWatch = new KubernetesCatalogWatch(CLIENT, properties, namespaceProvider);
		kubernetesCatalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		kubernetesCatalogWatch.postConstruct();

		when(CLIENT.endpoints()).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.inAnyNamespace()).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.withLabels(Map.of())).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.withNewFilter()).thenReturn(FILTER_NESTED);
		when(FILTER_NESTED.withLabels(Map.of())).thenReturn(FILTER_NESTED);
		when(FILTER_NESTED.endFilter()).thenReturn(FILTER_WATCH_LIST_DELETABLE);

	}

	private void createInSpecificNamespaceWatcher() {

		// all-namespaces = false
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, false);

		kubernetesCatalogWatch = new KubernetesCatalogWatch(CLIENT, properties, namespaceProvider);
		kubernetesCatalogWatch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		kubernetesCatalogWatch.postConstruct();

		when(namespaceProvider.getNamespace()).thenReturn("catalog-watcher-namespace");
		when(CLIENT.endpoints()).thenReturn(MIXED_OPERATION);
		when(MIXED_OPERATION.inNamespace("catalog-watcher-namespace")).thenReturn(NON_NAMESPACE_OPERATION);
		when(NON_NAMESPACE_OPERATION.withNewFilter()).thenReturn(FILTER_NESTED);
		when(FILTER_NESTED.withLabels(Map.of())).thenReturn(FILTER_NESTED);
		when(FILTER_NESTED.endFilter()).thenReturn(FILTER_WATCH_LIST_DELETABLE);
	}

	private void mockServicesCall(String name, String namespace) {
		MixedOperation<Service, ServiceList, ServiceResource<Service>> mixedOperation = Mockito
				.mock(MixedOperation.class);
		NonNamespaceOperation<Service, ServiceList, ServiceResource<Service>> nonNamespaceOperation = Mockito
				.mock(NonNamespaceOperation.class);
		when(CLIENT.services()).thenReturn(mixedOperation);
		when(mixedOperation.inNamespace(namespace)).thenReturn(nonNamespaceOperation);
		when(nonNamespaceOperation.list()).thenReturn(new ServiceListBuilder().withItems(
				new ServiceBuilder().withNewMetadata().withName(name).withNamespace(namespace).endMetadata().build())
				.build());
	}

}
