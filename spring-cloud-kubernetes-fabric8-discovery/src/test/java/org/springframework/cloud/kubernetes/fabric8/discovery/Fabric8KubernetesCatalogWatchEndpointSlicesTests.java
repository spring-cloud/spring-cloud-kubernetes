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
import io.fabric8.kubernetes.api.model.APIResourceList;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscovery;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscoveryBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceList;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Some tests that use the fabric8 mock client, using EndpointSlices
 *
 * @author wind57
 */
@EnableKubernetesMockClient
class Fabric8KubernetesCatalogWatchEndpointSlicesTests extends EndpointsAndEndpointSlicesTests {

	private static final Boolean ENDPOINT_SLICES = true;

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@AfterEach
	void afterEach() {
		endpointSlicesMockServer().clearExpectations();
	}

	/**
	 * <pre>
	 *     - we have 2 pods involved in this test
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podD in namespaceB with labels {color=blue}
	 *
	 *     We set the namespace to be "namespaceA" and search for labels {color=blue}
	 *     As a result only one pod is taken: podB
	 * </pre>
	 */
	@Test
	@Override
	void testInSpecificNamespaceWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels(
			"namespaceA", Map.of("color", "blue"), ENDPOINT_SLICES);

		createEndpointSlice("namespaceA", Map.of(), "podA");
		createEndpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		createEndpointSlice("namespaceA", Map.of("color", "red"), "podC");
		createEndpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		createEndpointSlice("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We set the namespace to be "namespaceA" and search without labels
	 *     As a result we get three pods:
	 *       - podA in namespaceA
	 *       - podB in namespaceA
	 *       - pocC in namespaceA
	 *
	 * </pre>
	 */
	@Test
	@Override
	void testInSpecificNamespaceWithoutServiceLabels() {

//		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels(
//			"namespaceA", Map.of(), ENDPOINT_SLICES);
//
//		EndpointSlice sliceA = createSingleEndpointWithEndpointSlices("namespaceA", Map.of(), "podA");
//		EndpointSlice sliceB = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "blue"), "podB");
//		EndpointSlice sliceC = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "red"), "podC");
//		EndpointSliceList listInNamespaceA = new EndpointSliceListBuilder().withItems(sliceA, sliceB, sliceC).build();
//		endpointSlicesMockServer().expect().withPath("/apis/discovery.k8s.io/v1/namespaces/namespaceA/endpointslices")
//				.andReturn(200, listInNamespaceA).once();
//
//		// this is mocked, but never supposed to be called
//		EndpointSlice sliceD = createSingleEndpointWithEndpointSlices("namespaceB", Map.of("color", "blue"), "podD");
//		EndpointSlice sliceE = createSingleEndpointWithEndpointSlices("namespaceB", Map.of(), "podE");
//		EndpointSliceList listInNamespaceB = new EndpointSliceListBuilder().withItems(sliceD, sliceE).build();
//		endpointSlicesMockServer().expect().withPath("/apis/discovery.k8s.io/v1/namespaces/namespaceB/endpointslices")
//				.andReturn(200, listInNamespaceB).once();
//
//		watch.catalogServicesWatch();
//
//		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
//
//		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
//		assertThat(event.getValue()).isInstanceOf(List.class);
//
//		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
//				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"));
//		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *
	 *     - we have 2 pods involved in this test
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podD in namespaceB with labels {color=blue}
	 *
	 *     We search in all namespaces with labels {color=blue}
	 *     As a result two pods are taken:
	 *       - podB in namespaceA
	 *       - podD in namespaceB
	 *
	 * </pre>
	 */
	@Test
	@Override
	void testInAllNamespacesWithServiceLabels() {

//		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(
//			Map.of("color", "blue"), Set.of(), ENDPOINT_SLICES);
//
//		EndpointSlice sliceB = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "blue"), "podB");
//		EndpointSlice sliceD = createSingleEndpointWithEndpointSlices("namespaceB", Map.of("color", "blue"), "podD");
//		EndpointSliceList listInAllNamespaces = new EndpointSliceListBuilder().withItems(sliceB, sliceD).build();
//		endpointSlicesMockServer().expect().withPath("/apis/discovery.k8s.io/v1/endpointslices?labelSelector=color%3Dblue")
//				.andReturn(200, listInAllNamespaces).once();
//
//		watch.catalogServicesWatch();
//
//		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
//
//		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
//		assertThat(event.getValue()).isInstanceOf(List.class);
//
//		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
//				new EndpointNameAndNamespace("podD", "namespaceB"));
//		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *
	 *     - we have 5 pods involved in this test
	 *     - podA in namespaceA with no labels
	 *     - podB in namespaceA with labels {color=blue}
	 *     - podC in namespaceA with labels {color=red}
	 *     - podD in namespaceB with labels {color=blue}
	 *     - podE in namespaceB with no labels
	 *
	 *     We search in all namespaces without labels
	 *     As a result we get all 5 pods
	 *
	 * </pre>
	 */
	@Test
	@Override
	void testInAllNamespacesWithoutServiceLabels() {

//		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(
//			Map.of(), Set.of(), ENDPOINT_SLICES);
//
//		EndpointSlice sliceA = createSingleEndpointWithEndpointSlices("namespaceA", Map.of(), "podA");
//		EndpointSlice sliceB = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "blue"), "podB");
//		EndpointSlice sliceC = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "red"), "podC");
//		EndpointSlice sliceD = createSingleEndpointWithEndpointSlices("namespaceB", Map.of("color", "blue"), "podD");
//		EndpointSlice sliceE = createSingleEndpointWithEndpointSlices("namespaceB", Map.of(), "podE");
//		EndpointSliceList listInAllNamespaces = new EndpointSliceListBuilder()
//				.withItems(sliceA, sliceB, sliceC, sliceD, sliceE).build();
//		endpointSlicesMockServer().expect().withPath("/apis/discovery.k8s.io/v1/endpointslices").andReturn(200, listInAllNamespaces)
//				.once();
//
//		watch.catalogServicesWatch();
//
//		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
//
//		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
//		assertThat(event.getValue()).isInstanceOf(List.class);
//
//		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
//				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"),
//				new EndpointNameAndNamespace("podD", "namespaceB"), new EndpointNameAndNamespace("podE", "namespaceB"));
//		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}


	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - namespaces = [namespaceB]
	 *
	 *     - we have 5 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 *
	 *     We search with labels = {color = blue}
	 *     Even if namespaces = [namespaceB], we still take podB and podD, because all-namespace=true
	 *
	 * </pre>
	 */
	@Test
	@Override
	void testAllNamespacesTrueOtherBranchesNotCalled() {

//		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(
//			Map.of("color", "blue"), Set.of("B"), ENDPOINT_SLICES);
//
//		EndpointSlice sliceA = createSingleEndpointWithEndpointSlices("namespaceA", Map.of(), "podA");
//		EndpointSlice sliceB = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "blue"), "podB");
//		EndpointSlice sliceC = createSingleEndpointWithEndpointSlices("namespaceA", Map.of("color", "red"), "podC");
//		EndpointSlice sliceD = createSingleEndpointWithEndpointSlices("namespaceB", Map.of("color", "blue"), "podD");
//		EndpointSlice sliceE = createSingleEndpointWithEndpointSlices("namespaceB", Map.of(), "podE");
//		EndpointSliceList listInAllNamespaces = new EndpointSliceListBuilder()
//			.withItems(sliceA, sliceB, sliceC, sliceD, sliceE).build();
//		endpointSlicesMockServer().expect().withPath("/apis/discovery.k8s.io/v1/endpointslices?labelSelector=color%3Dblue")
//			.andReturn(200, listInAllNamespaces)
//			.once();
//
//		watch.catalogServicesWatch();
//
//		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());
//
//		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
//		assertThat(event.getValue()).isInstanceOf(List.class);
//
//		List<EndpointNameAndNamespace> expectedOutput = List.of(
//			new EndpointNameAndNamespace("podB", "namespaceA"),
//			new EndpointNameAndNamespace("podD", "namespaceB")
//		);
//		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	/**
	 * <pre>
	 *     - endpoint slices are enabled, but are not supported by the cluster, as such we will fail
	 *       with an IllegalArgumentException
	 *     - ApiGroups is empty
	 * </pre>
	 */
	@Test
	void testEndpointSlicesEnabledButNotSupportedViaApiGroups() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, true);

		APIGroupList groupList = new APIGroupListBuilder().build();
		mockServer.expect().withPath("/apis").andReturn(200, groupList).always();

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(endpointSlicesMockClient(), properties, NAMESPACE_PROVIDER);
		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, watch::postConstruct);
		Assertions.assertEquals("EndpointSlices are not supported on the cluster", ex.getMessage());
	}

	/**
	 * <pre>
	 *     - endpoint slices are enabled, but are not supported by the cluster, as such we will fail
	 *       with an IllegalArgumentException
	 *     - ApiVersions is empty
	 * </pre>
	 */
	@Test
	void testEndpointSlicesEnabledButNotSupportedViaApiVersions() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, "", Set.of(), Map.of(), "", null, 0, true);

		GroupVersionForDiscovery forDiscovery = new GroupVersionForDiscoveryBuilder()
				.withGroupVersion("discovery.k8s.io/v1").build();
		APIGroup apiGroup = new APIGroupBuilder().withApiVersion("v1").withVersions(forDiscovery).build();
		APIGroupList groupList = new APIGroupListBuilder().withGroups(apiGroup).build();
		mockServer.expect().withPath("/apis").andReturn(200, groupList).always();

		APIResourceList apiResourceList = new APIResourceListBuilder().build();
		mockServer.expect().withPath("/apis/discovery.k8s.io/v1").andReturn(200, apiResourceList).always();

		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(endpointSlicesMockClient(), properties, NAMESPACE_PROVIDER);
		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, watch::postConstruct);
		Assertions.assertEquals("EndpointSlices are not supported on the cluster", ex.getMessage());
	}

	// work-around for : https://github.com/fabric8io/kubernetes-client/issues/4649
	static KubernetesClient endpointSlicesMockClient() {
		return mockClient;
	}

	static KubernetesMockServer endpointSlicesMockServer() {
		return mockServer;
	}

}
