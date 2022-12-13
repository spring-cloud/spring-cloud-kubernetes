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

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

/**
 * Some tests that use the fabric8 mock client, using EndpointSlices
 *
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesCatalogWatchEndpointSlicesTests extends Fabric8EndpointsAndEndpointSlicesTests {

	private static final Boolean ENDPOINT_SLICES = true;

	private static KubernetesClient mockClient;

	@Test
	@Override
	void testInSpecificNamespaceWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("namespaceA", Map.of("color", "blue"),
				ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podB", "namespaceA")));
	}

	@Test
	@Override
	void testInSpecificNamespaceWithoutServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("namespaceA", Map.of(),
				ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch,
				List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
						new EndpointNameAndNamespace("podB", "namespaceA"),
						new EndpointNameAndNamespace("podC", "namespaceA")));
	}

	@Test
	@Override
	void testInAllNamespacesWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesWithLabels(Map.of("color", "blue"), Set.of(),
				ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB")));
	}

	@Test
	@Override
	void testInAllNamespacesWithoutServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesWithLabels(Map.of(), Set.of(), ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podA", "namespaceA"),
				new EndpointNameAndNamespace("podB", "namespaceA"), new EndpointNameAndNamespace("podC", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB"),
				new EndpointNameAndNamespace("podE", "namespaceB")));
	}

	@Test
	@Override
	void testAllNamespacesTrueOtherBranchesNotCalled() {

		KubernetesCatalogWatch watch = createWatcherInAllNamespacesWithLabels(Map.of("color", "blue"), Set.of("B"),
				ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
				new EndpointNameAndNamespace("podD", "namespaceB")));
	}

	@Test
	@Override
	void testAllNamespacesFalseNamespacesPresent() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesWithLabels(Set.of("namespaceA"),
				Map.of("color", "blue"), ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podB", "namespaceA")));
	}

	@Test
	@Override
	void testAllNamespacesFalseNamespacesNotPresent() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("namespaceA", Map.of("color", "blue"),
				ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podB", "namespaceA")));
	}

	@Test
	@Override
	void testTwoNamespacesOutOfThree() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespacesWithLabels(Set.of("namespaceA", "namespaceB"),
				Map.of("color", "blue"), ENDPOINT_SLICES);

		endpointSlice("namespaceA", Map.of(), "podA");
		endpointSlice("namespaceA", Map.of("color", "blue"), "podB");
		endpointSlice("namespaceA", Map.of("color", "red"), "podC");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podD");
		endpointSlice("namespaceB", Map.of(), "podE");
		endpointSlice("namespaceB", Map.of("color", "blue"), "podF");
		endpointSlice("namespaceC", Map.of("color", "blue"), "podO");

		invokeAndAssert(watch,
				List.of(new EndpointNameAndNamespace("podB", "namespaceA"),
						new EndpointNameAndNamespace("podD", "namespaceB"),
						new EndpointNameAndNamespace("podF", "namespaceB")));
	}

	// work-around for : https://github.com/fabric8io/kubernetes-client/issues/4649
	static KubernetesClient endpointSlicesMockClient() {
		return mockClient;
	}

}
