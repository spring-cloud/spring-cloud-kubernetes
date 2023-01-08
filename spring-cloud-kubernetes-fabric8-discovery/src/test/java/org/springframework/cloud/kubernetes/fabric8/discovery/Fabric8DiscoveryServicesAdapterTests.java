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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.fabric8.Fabric8Utils;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryServicesAdapterTests {

	private static KubernetesClient client;

	private static MockedStatic<Fabric8Utils> utils;

	@BeforeEach
	void beforeEach() {
		utils = Mockito.mockStatic(Fabric8Utils.class);
	}

	@AfterEach
	void afterEach() {
		client.services().inAnyNamespace().delete();
		utils.close();
	}

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

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

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

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

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
	void testAllNamespacesWithoutLabelsWithNamespaceFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of();
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

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
	 *     - filter = "#root.metadata.namespace matches '^namespace[A|B]$'"
	 *       (namespaceA or namespaceB)
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceB exists in namespaceB with labels = {color=blue}
	 *     - serviceC exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get only serviceA and serviceB as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithoutLabelsWithNamespacesFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of();
		String spelFilter = """
				#root.metadata.namespace matches "^namespace[A|B]$"
				""";

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceB", Map.of("color", "blue"));
		service("namespaceC", "serviceC", Map.of("color", "purple"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
		Assertions.assertEquals(result.get(1).getMetadata().getName(), "serviceB");
		Assertions.assertEquals(result.get(1).getMetadata().getNamespace(), "namespaceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - specific namespace = namespaceA
	 *     - labels = {}
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceB exists in namespaceB with labels = {color=blue}
	 *
	 *     - we get only serviceA as a result.
	 * </pre>
	 */
	@Test
	void testSpecificNamespaceWithoutLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Map<String, String> labels = Map.of();
		String spelFilter = null;

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		utils.when(() -> Fabric8Utils.getApplicationNamespace(Mockito.any(KubernetesClient.class),
				Mockito.nullable(String.class), Mockito.anyString(), Mockito.any(KubernetesNamespaceProvider.class)))
				.thenReturn("namespaceA");

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceB", Map.of("color", "blue"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - specific namespace = namespaceA
	 *     - labels = {color = purple}
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceB exists in namespaceA with labels = {color=purple}
	 *     - serviceC exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get only serviceB as a result, even if such labels are also
	 *       present on a different service (but it's in a different namespace).
	 * </pre>
	 */
	@Test
	void testSpecificNamespaceWithLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Map<String, String> labels = Map.of("color", "purple");
		String spelFilter = null;

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		utils.when(() -> Fabric8Utils.getApplicationNamespace(Mockito.any(KubernetesClient.class),
				Mockito.nullable(String.class), Mockito.anyString(), Mockito.any(KubernetesNamespaceProvider.class)))
				.thenReturn("namespaceA");

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceA", "serviceB", Map.of("color", "purple"));
		service("namespaceC", "serviceC", Map.of("color", "purple"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - specific namespace = namespaceA
	 *     - labels = {}
	 *     - filter = "#root.metadata.labels.containsKey("number")"
	 *       (namespaceA or namespaceB)
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red, number=1}
	 *     - serviceB exists in namespaceA with labels = {color=purple, cycle=create}
	 *     - serviceC exists in namespaceC with labels = {color=purple, number=1}
	 *
	 *     - we get only serviceB as a result (because of the filter) even if such labels are also
	 *       present on a different service (but it's in a different namespace).
	 * </pre>
	 */
	@Test
	void testSpecificNamespaceWithoutLabelsWithFilter() {
		boolean allNamespaces = false;
		Map<String, String> labels = Map.of();
		String spelFilter = """
							#root.metadata.labels.containsKey("number")
				""".stripLeading();

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		utils.when(() -> Fabric8Utils.getApplicationNamespace(Mockito.any(KubernetesClient.class),
				Mockito.nullable(String.class), Mockito.anyString(), Mockito.any(KubernetesNamespaceProvider.class)))
				.thenReturn("namespaceA");

		service("namespaceA", "serviceA", Map.of("color", "red", "number", "1"));
		service("namespaceA", "serviceB", Map.of("color", "purple", "cycle", "create"));
		service("namespaceC", "serviceC", Map.of("color", "purple", "number", "1"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
		Assertions.assertEquals(result.get(0).getMetadata().getLabels(), Map.of("color", "red", "number", "1"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - some namespaces = [namespaceA, namespaceB]
	 *     - labels = {}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {}
	 *     - serviceB exists in namespaceB with labels = {}
	 *     - serviceC exists in namespaceC with labels = {}
	 *
	 *     - we get serviceA and serviceB as a result, because their namespaces match.
	 * </pre>
	 */
	@Test
	void testSomeNamespacesWithoutLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Set<String> someNamespaces = Set.of("namespaceA", "namespaceB");
		Map<String, String> labels = Map.of();
		String spelFilter = null;

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces,
				someNamespaces, true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		service("namespaceA", "serviceA", Map.of());
		service("namespaceB", "serviceB", Map.of());
		service("namespaceC", "serviceC", Map.of());

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 2);
		result = result.stream().sorted(Comparator.comparing(x -> x.getMetadata().getName())).toList();
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
		Assertions.assertEquals(result.get(1).getMetadata().getName(), "serviceB");
		Assertions.assertEquals(result.get(1).getMetadata().getNamespace(), "namespaceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - some namespaces = [namespaceA, namespaceB]
	 *     - labels = {color=purple}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {color=purple}
	 *     - serviceB exists in namespaceB with labels = {color=red}
	 *     - serviceC exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get serviceA as a result
	 * </pre>
	 */
	@Test
	void testSomeNamespacesWithLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Set<String> someNamespaces = Set.of("namespaceA", "namespaceB");
		Map<String, String> labels = Map.of("color", "purple");
		String spelFilter = null;

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces,
				someNamespaces, true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		service("namespaceA", "serviceA", Map.of("color", "purple"));
		service("namespaceB", "serviceB", Map.of("color", "red"));
		service("namespaceC", "serviceC", Map.of("color", "purple"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - some namespaces = [namespaceA, namespaceB]
	 *     - labels = {color=purple}
	 *     - filter = #root.metadata.labels.containsKey("number")
	 *
	 *     - serviceA exists in namespaceA with labels = {color=purple}
	 *     - serviceB exists in namespaceB with labels = {color=red}
	 *     - serviceC exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get serviceA as a result
	 * </pre>
	 */
	@Test
	void testSomeNamespacesWithLabelsWithFilter() {
		boolean allNamespaces = false;
		Set<String> someNamespaces = Set.of("namespaceA", "namespaceB");
		Map<String, String> labels = Map.of("color", "purple");
		String spelFilter = """
							#root.metadata.labels.containsKey("number")
				""".stripLeading();

		MockEnvironment environment = new MockEnvironment();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces,
				someNamespaces, true, 60L, false, spelFilter, Set.of(), labels, null, null, 0, false);

		Fabric8DiscoveryServicesAdapter adapter = new Fabric8DiscoveryServicesAdapter(
				new KubernetesDiscoveryClientAutoConfiguration().servicesFunction(properties, environment), properties,
				null);

		service("namespaceA", "serviceA", Map.of("color", "purple", "number", "1"));
		service("namespaceB", "serviceB", Map.of("color", "purple", "cycle", "create"));
		service("namespaceC", "serviceC", Map.of("color", "purple", "number", "1"));

		List<Service> result = adapter.apply(client);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespaceA");
	}

	private void service(String namespace, String name, Map<String, String> labels) {
		client.services().inNamespace(namespace)
				.resource(new ServiceBuilder().withNewMetadata().withName(name).withLabels(labels).and().build())
				.create();
	}

}
