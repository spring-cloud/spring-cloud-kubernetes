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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.APIGroup;
import io.fabric8.kubernetes.api.model.APIGroupBuilder;
import io.fabric8.kubernetes.api.model.APIGroupList;
import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.kubernetes.api.model.APIResource;
import io.fabric8.kubernetes.api.model.APIResourceBuilder;
import io.fabric8.kubernetes.api.model.APIResourceList;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscovery;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscoveryBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.when;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesCatalogWatchEndpointSlicesTests.endpointSlicesMockServer;

/**
 * make sure that all the tests for endpoints are also handled by endpoint slices
 *
 * @author wind57
 */
abstract class EndpointsAndEndpointSlicesTests {

	static final KubernetesNamespaceProvider NAMESPACE_PROVIDER = Mockito.mock(KubernetesNamespaceProvider.class);

	static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
		.forClass(HeartbeatEvent.class);

	static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito
		.mock(ApplicationEventPublisher.class);

	@BeforeAll
	static void setUp() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient().getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	void afterEach() {
		Mockito.reset(APPLICATION_EVENT_PUBLISHER);
		mockClient().endpoints().inAnyNamespace().delete();
	}

	abstract void testInSpecificNamespaceWithServiceLabels();

	abstract void testInSpecificNamespaceWithoutServiceLabels();

	abstract void testInAllNamespacesWithServiceLabels();

	abstract void testInAllNamespacesWithoutServiceLabels();

	abstract void testAllNamespacesTrueOtherBranchesNotCalled();

	KubernetesCatalogWatch createWatcherInAllNamespacesAndLabels(Map<String, String> labels,
			Set<String> namespaces, boolean endpointSlices) {

		if (endpointSlices) {
			createEndpointSlicesApiGroup();
		}

		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces, true, 60,
			false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		watch.postConstruct();
		return watch;

	}

	KubernetesCatalogWatch createWatcherInSpecificNamespaceAndLabels(String namespace,
			Map<String, String> labels, boolean endpointSlices) {

		if (endpointSlices) {
			createEndpointSlicesApiGroup();
		}

		when(NAMESPACE_PROVIDER.getNamespace()).thenReturn(namespace);

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
			Set.of(namespace), true, 60, false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		watch.postConstruct();
		return watch;

	}

	KubernetesCatalogWatch createWatcherInSpecificNamespacesAndLabels(Set<String> namespaces,
			Map<String, String> labels) {

		// all-namespaces = false
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, namespaces, true, 60,
			false, "", Set.of(), labels, "", null, 0, false);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		watch.postConstruct();
		return watch;

	}

	void createSingleEndpoints(String namespace, Map<String, String> labels, String podName) {

		EndpointAddress endpointAddress = new EndpointAddressBuilder()
			.withTargetRef(new ObjectReferenceBuilder().withName(podName).withNamespace(namespace).build()).build();

		EndpointSubset endpointSubset = new EndpointSubsetBuilder().withAddresses(List.of(endpointAddress)).build();

		Endpoints endpoints = new EndpointsBuilder()
			.withMetadata(new ObjectMetaBuilder().withLabels(labels).withName("endpoints-" + podName).build())
			.withSubsets(List.of(endpointSubset)).build();
		mockClient().endpoints().inNamespace(namespace).create(endpoints);
	}

	// mock KubernetesCatalogWatch::postConstruct
	private static void createEndpointSlicesApiGroup() {

		GroupVersionForDiscovery forDiscovery = new GroupVersionForDiscoveryBuilder()
			.withGroupVersion("discovery.k8s.io/v1").build();
		APIGroup apiGroup = new APIGroupBuilder().withApiVersion("v1").withVersions(forDiscovery).build();
		APIGroupList groupList = new APIGroupListBuilder().withGroups(apiGroup).build();
		endpointSlicesMockServer()
			.expect().withPath("/apis").andReturn(200, groupList).always();

		APIResource apiResource = new APIResourceBuilder().withKind("EndpointSlice").build();
		APIResourceList apiResourceList = new APIResourceListBuilder().withResources(apiResource).build();
		endpointSlicesMockServer()
			.expect().withPath("/apis/discovery.k8s.io/v1").andReturn(200, apiResourceList).always();

	}

	static void createEndpointSlice(String namespace, Map<String, String> labels,
			String podName) {

		Endpoint endpoint = new EndpointBuilder()
			.withTargetRef(new ObjectReferenceBuilder().withName(podName).withNamespace(namespace).build()).build();

		EndpointSlice slice = new EndpointSliceBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build())
			.withEndpoints(endpoint).build();

		mockClient().discovery().v1().endpointSlices().resource(slice).create();
	}

	private static KubernetesClient mockClient() {
		return Fabric8KubernetesCatalogWatchEndpointsTests.endpointsMockClient() != null ?
			Fabric8KubernetesCatalogWatchEndpointsTests.endpointsMockClient() :
			Fabric8KubernetesCatalogWatchEndpointSlicesTests.endpointSlicesMockClient();
	}

}
