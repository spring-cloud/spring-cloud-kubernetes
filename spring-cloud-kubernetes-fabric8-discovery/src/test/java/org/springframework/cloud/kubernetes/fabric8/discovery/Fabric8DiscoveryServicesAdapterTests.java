/*
 * Copyright 2012-2022 the original author or authors.
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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryServicesAdapterTests {

	private static KubernetesClient client;

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceB exists in namespaceB with labels = {color=blue}
	 *
	 *     - we get both services as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithoutLabelsWithoutFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of();
		String spelFilter = null;

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(
			false, allNamespaces, Set.of(), true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false
		);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
			new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties
		);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceB", Map.of("color", "blue"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
		Assertions.assertEquals(result.get(1).getMetadata().getName(), "serviceB");
		Assertions.assertEquals(result.get(1).getMetadata().getNamespace(), "namespaceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {color=red}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceB exists in namespaceB with labels = {color=blue}
	 *
	 *     - we get only serviceA as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithLabelsWithoutFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of("color", "red");
		String spelFilter = null;

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(
			false, allNamespaces, Set.of(), true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false
		);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
			new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties
		);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceB", Map.of("color", "blue"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {}
	 *     - filter = "#root.metadata.namespace matches '^.+A$'"
	 *       (ends in A)
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceB exists in namespaceB with labels = {color=blue}
	 *
	 *     - we get only serviceA as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithoutLabelsWithFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of();
		String spelFilter = """
			#root.metadata.namespace matches "namespaceA"
			""";

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(
			false, allNamespaces, Set.of(), true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false
		);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
			new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties
		);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceB", Map.of("color", "blue"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
	}

	private void service(String namespace, String name, Map<String, String> labels) {
		client.services().inNamespace(namespace).resource(new ServiceBuilder().withNewMetadata()
			.withName(name).withLabels(labels).and().build()).create();
	}

}
