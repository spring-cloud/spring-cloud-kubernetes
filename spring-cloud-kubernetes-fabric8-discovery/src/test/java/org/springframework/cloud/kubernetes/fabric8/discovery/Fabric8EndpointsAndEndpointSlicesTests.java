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

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
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
abstract class Fabric8EndpointsAndEndpointSlicesTests {

	static final KubernetesNamespaceProvider NAMESPACE_PROVIDER = Mockito.mock(KubernetesNamespaceProvider.class);

	static final ArgumentCaptor<HeartbeatEvent> HEARTBEAT_EVENT_ARGUMENT_CAPTOR = ArgumentCaptor
			.forClass(HeartbeatEvent.class);

	static final ApplicationEventPublisher APPLICATION_EVENT_PUBLISHER = Mockito.mock(ApplicationEventPublisher.class);

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
		mockClient().discovery().v1().endpointSlices().inAnyNamespace().delete();
		mockClient().endpoints().inAnyNamespace().delete();
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
	 *     We set the namespace to be "namespaceA" and search for labels {color=blue}
	 *     As a result only one pod is taken: podB
	 *
	 * </pre>
	 */
	abstract void testInSpecificNamespaceWithServiceLabels();

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
	abstract void testInSpecificNamespaceWithoutServiceLabels();

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
	 *     We search in all namespaces with labels {color=blue}
	 *     As a result two pods are taken:
	 *       - podB in namespaceA
	 *       - podD in namespaceB
	 *
	 * </pre>
	 */
	abstract void testInAllNamespacesWithServiceLabels();

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
	abstract void testInAllNamespacesWithoutServiceLabels();

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
	abstract void testAllNamespacesTrueOtherBranchesNotCalled();

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - namespaces = [namespaceA]
	 *
	 *     - we have 5 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 *
	 *     We search with labels = {color = blue}
	 *     Since namespaces = [namespaceA], we wil take podB, because all-namespace=false (podD is not part of the response)
	 *
	 * </pre>
	 */
	abstract void testAllNamespacesFalseNamespacesPresent();

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - namespaces = []
	 *
	 *     - we have 5 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 *
	 *     We search with labels = {color = blue}
	 *     Since namespaces = [], we wil take podB, because all-namespace=false (podD is not part of the response)
	 *
	 * </pre>
	 */
	abstract void testAllNamespacesFalseNamespacesNotPresent();

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - namespaces = [namespaceA, namespaceB]
	 *
	 *     - we have 7 pods involved in this test
	 * 	   - podA in namespaceA with no labels
	 * 	   - podB in namespaceA with labels {color=blue}
	 * 	   - podC in namespaceA with labels {color=red}
	 * 	   - podD in namespaceB with labels {color=blue}
	 * 	   - podE in namespaceB with no labels
	 * 	   - podF in namespaceB with labels {color=blue}
	 * 	   - podO in namespaceC with labels {color=blue}
	 *
	 *     We search with labels = {color = blue}
	 *     Since namespaces = [namespaceA, namespaceB], we wil take podB, podD and podF,
	 *     but will not take podO
	 *
	 * </pre>
	 */
	abstract void testTwoNamespacesOutOfThree();

	KubernetesCatalogWatch createWatcherInAllNamespacesWithLabels(Map<String, String> labels, Set<String> namespaces,
			boolean endpointSlices) {

		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60, false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new Fabric8EndpointSliceV1CatalogWatch()).when(watch).stateGenerator();
		}

		watch.postConstruct();
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}

	KubernetesCatalogWatch createWatcherInSpecificNamespaceWithLabels(String namespace, Map<String, String> labels,
			boolean endpointSlices) {

		when(NAMESPACE_PROVIDER.getNamespace()).thenReturn(namespace);

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of(namespace), true, 60, false, "", Set.of(), labels, "", null, 0, endpointSlices);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new Fabric8EndpointSliceV1CatalogWatch()).when(watch).stateGenerator();
		}

		watch.postConstruct();
		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		return watch;

	}

	KubernetesCatalogWatch createWatcherInSpecificNamespacesWithLabels(Set<String> namespaces,
			Map<String, String> labels, boolean endpointSlices) {

		// all-namespaces = false
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, namespaces, true, 60,
				false, "", Set.of(), labels, "", null, 0, false);
		KubernetesCatalogWatch watch = new KubernetesCatalogWatch(mockClient(), properties, NAMESPACE_PROVIDER);

		if (endpointSlices) {
			watch = Mockito.spy(watch);
			Mockito.doReturn(new Fabric8EndpointSliceV1CatalogWatch()).when(watch).stateGenerator();
		}

		watch.setApplicationEventPublisher(APPLICATION_EVENT_PUBLISHER);
		watch.postConstruct();
		return watch;

	}

	void endpoints(String namespace, Map<String, String> labels, String podName) {

		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withName(podName).withNamespace(namespace).build()).build();

		EndpointSubset endpointSubset = new EndpointSubsetBuilder().withAddresses(List.of(endpointAddress)).build();

		Endpoints endpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(labels).withName("endpoints-" + podName).build())
				.withSubsets(List.of(endpointSubset)).build();
		mockClient().endpoints().inNamespace(namespace).resource(endpoints).create();
	}

	void service(String namespace, Map<String, String> labels, String podName) {

		Service service = new ServiceBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(labels).withName("endpoints-" + podName).build())
				.build();
		mockClient().services().inNamespace(namespace).resource(service).create();
	}

	static void endpointSlice(String namespace, Map<String, String> labels, String podName) {

		Endpoint endpoint = new EndpointBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withName(podName).withNamespace(namespace).build()).build();

		EndpointSlice slice = new EndpointSliceBuilder().withMetadata(new ObjectMetaBuilder().withNamespace(namespace)
				.withName("slice-" + podName).withLabels(labels).build()).withEndpoints(endpoint).build();

		mockClient().discovery().v1().endpointSlices().inNamespace(namespace).resource(slice).create();

	}

	static void invokeAndAssert(KubernetesCatalogWatch watch, List<EndpointNameAndNamespace> state) {
		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER, Mockito.atLeastOnce())
				.publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		assertThat(event.getValue()).isEqualTo(state);
	}

	// work-around for : https://github.com/fabric8io/kubernetes-client/issues/4649
	private static KubernetesClient mockClient() {
		return Fabric8KubernetesCatalogWatchEndpointsTests.endpointsMockClient() != null
				? Fabric8KubernetesCatalogWatchEndpointsTests.endpointsMockClient()
				: Fabric8KubernetesCatalogWatchEndpointSlicesTests.endpointSlicesMockClient();
	}

}
