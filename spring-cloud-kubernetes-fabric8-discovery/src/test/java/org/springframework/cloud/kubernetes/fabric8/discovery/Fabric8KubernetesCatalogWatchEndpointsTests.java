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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Tests for endpoints based catalog watch
 *
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesCatalogWatchEndpointsTests extends EndpointsAndEndpointSlicesTests {

	private static final Boolean ENDPOINT_SLICES = false;

	private static KubernetesClient mockClient;

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
	@Test
	@Override
	void testInSpecificNamespaceWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels(
			"namespaceA", Map.of("color", "blue"), ENDPOINT_SLICES);

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

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

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels(
			"namespaceA", Map.of(), ENDPOINT_SLICES);

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"));
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

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(
			Map.of("color", "blue"), Set.of(), ENDPOINT_SLICES);

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"));
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
	 *     We search in all namespaces without labels
	 *     As a result we get all 5 pods
	 *
	 * </pre>
	 */
	@Test
	@Override
	void testInAllNamespacesWithoutServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(
			Map.of(), Set.of(), ENDPOINT_SLICES);

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"), new EndpointNameAndNamespace("podE", "namespaceB"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
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

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesAndLabels(
			Map.of("color", "blue"), Set.of("B"), ENDPOINT_SLICES);

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

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
	@Test
	void testAllNamespacesFalseNamespacesPresent() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesAndLabels(Set.of("namespaceA"),
				Map.of("color", "blue"));

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

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
	@Test
	void testAllNamespacesFalseNamespacesNotPresent() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceAndLabels(
			"namespaceA", Map.of("color", "blue"), ENDPOINT_SLICES);

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

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
	@Test
	void testTwoNamespacesOutOfThree() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesAndLabels(Set.of("namespaceA", "namespaceB"),
				Map.of("color", "blue"));

		createSingleEndpoints("namespaceA", Map.of(), "podA");
		createSingleEndpoints("namespaceA", Map.of("color", "blue"), "podB");
		createSingleEndpoints("namespaceA", Map.of("color", "red"), "podC");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podD");
		createSingleEndpoints("namespaceB", Map.of(), "podE");
		createSingleEndpoints("namespaceB", Map.of("color", "blue"), "podF");
		createSingleEndpoints("namespaceC", Map.of("color", "blue"), "podO");

		watch.catalogServicesWatch();

		verify(APPLICATION_EVENT_PUBLISHER).publishEvent(HEARTBEAT_EVENT_ARGUMENT_CAPTOR.capture());

		HeartbeatEvent event = HEARTBEAT_EVENT_ARGUMENT_CAPTOR.getValue();
		assertThat(event.getValue()).isInstanceOf(List.class);

		List<EndpointNameAndNamespace> expectedOutput = List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"), new EndpointNameAndNamespace("podF", "namespaceB"));
		assertThat(event.getValue()).isEqualTo(expectedOutput);
	}

	// work-around for : https://github.com/fabric8io/kubernetes-client/issues/4649
	static KubernetesClient endpointsMockClient() {
		return mockClient;
	}

}
