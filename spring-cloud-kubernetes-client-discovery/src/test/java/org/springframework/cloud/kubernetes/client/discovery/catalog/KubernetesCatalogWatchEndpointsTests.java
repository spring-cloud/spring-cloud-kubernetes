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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

import java.util.List;
import java.util.Map;

/**
 * Test cases for the Endpoints support
 *
 * @author wind57
 */
class KubernetesCatalogWatchEndpointsTests extends KubernetesEndpointsAndEndpointSlicesTests {

	@Override
	@Test
	void testInSpecificNamespaceWithServiceLabels() {

		KubernetesCatalogWatch watch = createWatcherInSpecificNamespaceWithLabels("namespaceA", Map.of("color", "blue"),
			ENDPOINT_SLICES);

		endpoints("namespaceA", Map.of(), "podA");
		endpoints("namespaceA", Map.of("color", "blue"), "podB");
		endpoints("namespaceA", Map.of("color", "red"), "podC");
		endpoints("namespaceB", Map.of("color", "blue"), "podD");
		endpoints("namespaceB", Map.of(), "podE");

		invokeAndAssert(watch, List.of(new EndpointNameAndNamespace("podB", "namespaceA")));

	}

	@Override
	void testInSpecificNamespaceWithoutServiceLabels() {

	}

	@Override
	void testInAllNamespacesWithServiceLabels() {

	}

	@Override
	void testInAllNamespacesWithoutServiceLabels() {

	}

	@Override
	void testAllNamespacesTrueOtherBranchesNotCalled() {

	}

	@Override
	void testAllNamespacesFalseNamespacesPresent() {

	}

	@Override
	void testAllNamespacesFalseNamespacesNotPresent() {

	}

	@Override
	void testTwoNamespacesOutOfThree() {

	}
}
