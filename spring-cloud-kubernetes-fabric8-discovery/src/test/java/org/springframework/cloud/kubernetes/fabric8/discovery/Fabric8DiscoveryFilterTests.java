/*
 * Copyright 2012-present the original author or authors.
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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;


/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryFilterTests extends Fabric8DiscoveryClientBase {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.services().inAnyNamespace().delete();
		client.endpoints().inAnyNamespace().delete();
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceA exists in namespaceB with labels = {color=blue}
	 *
	 *     - because we have no labels filtering in the informers/properties, it means
	 *       we get both services as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithoutLabelsWithoutFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
			true, 60L, false, null, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceA", Map.of("color", "blue"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "blue"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("namespaceA", "namespaceB"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {color=red}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceA exists in namespaceB with labels = {color=blue}
	 *
	 *     - we get only serviceA as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithLabelsWithoutFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of("color", "red");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
			true, 60L, false, null, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceA", Map.of("color", "blue"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "blue"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {}
	 *     - filter = "#root.metadata.namespace matches '^.+A$'"
	 *       (ends in A)
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceA exists in namespaceB with labels = {color=blue}
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

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceA", Map.of("color", "blue"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "blue"));

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
			true, 60L, false, spelFilter, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - labels = {}
	 *     - filter = "#root.metadata.namespace matches '^namespace[A|B]$'"
	 *       (namespaceA or namespaceB)
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceA exists in namespaceB with labels = {color=blue}
	 *     - serviceA exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get only serviceA from namespaceA and serviceA from namespaceB as a result.
	 * </pre>
	 */
	@Test
	void testAllNamespacesWithoutLabelsWithNamespacesFilter() {
		boolean allNamespaces = true;
		Map<String, String> labels = Map.of();
		String spelFilter = """
				#root.metadata.namespace matches "^namespace[A|B]$"
				""";

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
			true, 60L, false, spelFilter, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceA", Map.of("color", "blue"));
		service("namespaceC", "serviceA", Map.of("color", "purple"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "blue"));
		endpoints("namespaceC", "serviceA", Map.of("color", "purple"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("namespaceA", "namespaceB"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - specific namespace = namespaceA
	 *     - labels = {}
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceA exists in namespaceB with labels = {color=blue}
	 *
	 *     - we get only serviceA as a result.
	 * </pre>
	 */
	@Test
	void testSpecificNamespaceWithoutLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Map<String, String> labels = Map.of();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
				true, 60L, false, null, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceB", Map.of("color", "blue"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "blue"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespaceA"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - specific namespace = namespaceA, namespaceB
	 *     - labels = {color = purple}
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red}
	 *     - serviceA exists in namespaceB with labels = {color=purple}
	 *     - serviceA exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get only serviceA from namespaceB as a result, even if such labels are also
	 *       present on a different service (but it's in a different namespace).
	 * </pre>
	 */
	@Test
	void testSpecificNamespaceWithLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Map<String, String> labels = Map.of("color", "purple");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
			true, 60L, false, null, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "red"));
		service("namespaceB", "serviceA", Map.of("color", "purple"));
		service("namespaceC", "serviceA", Map.of("color", "purple"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "purple"));
		endpoints("namespaceC", "serviceA", Map.of("color", "purple"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespaceA", "namespaceB"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - specific namespace = namespaceA, namespaceB
	 *     - labels = {}
	 *     - filter = "#root.metadata.labels.containsKey("number")"
	 *       (namespaceA or namespaceC)
	 *
	 *     - serviceA exists in namespaceA with labels = {color=red, number=1}
	 *     - serviceA exists in namespaceB with labels = {color=purple, cycle=create}
	 *     - serviceA exists in namespaceC with labels = {color=purple, number=1}
	 *
	 *     - we get only serviceA from namespaceB as a result (because of the filter) even if such labels are also
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

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(),
			true, 60L, false, spelFilter, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "red", "number", "1"));
		service("namespaceB", "serviceA", Map.of("color", "purple", "cycle", "create"));
		service("namespaceC", "serviceA", Map.of("color", "purple", "number", "1"));

		endpoints("namespaceA", "serviceA", Map.of("color", "red"));
		endpoints("namespaceB", "serviceA", Map.of("color", "purple"));
		endpoints("namespaceC", "serviceA", Map.of("color", "purple"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespaceA", "namespaceB"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces = [namespaceA, namespaceB]
	 *     - labels = {}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {}
	 *     - serviceA exists in namespaceB with labels = {}
	 *     - serviceA exists in namespaceC with labels = {}
	 *
	 *     - we get serviceA in namespaceA and serviceA in namespaceB as a result, because their namespaces match.
	 * </pre>
	 */
	@Test
	void testSomeNamespacesWithoutLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Set<String> someNamespaces = Set.of("namespaceA", "namespaceB");
		Map<String, String> labels = Map.of();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces,
			someNamespaces, true, 60L, false, null, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of());
		service("namespaceB", "serviceA", Map.of());
		service("namespaceC", "serviceA", Map.of());

		endpoints("namespaceA", "serviceA", Map.of());
		endpoints("namespaceB", "serviceA", Map.of());
		endpoints("namespaceC", "serviceA", Map.of());

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespaceA", "namespaceB"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("namespaceA", "namespaceB"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - some namespaces = [namespaceA, namespaceB]
	 *     - labels = {color=purple}
	 *     - filter = null
	 *
	 *     - serviceA exists in namespaceA with labels = {color=purple}
	 *     - serviceA exists in namespaceB with labels = {color=red}
	 *     - serviceA exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get serviceA in namespaceA as a result
	 * </pre>
	 */
	@Test
	void testSomeNamespacesWithLabelsWithoutFilter() {
		boolean allNamespaces = false;
		Set<String> someNamespaces = Set.of("namespaceA", "namespaceB");
		Map<String, String> labels = Map.of("color", "purple");
		String spelFilter = null;

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces,
			someNamespaces, true, 60L, false, spelFilter, Set.of(), labels, null,
			KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "purple"));
		service("namespaceB", "serviceA", Map.of("color", "red"));
		service("namespaceC", "serviceA", Map.of("color", "purple"));

		endpoints("namespaceA", "serviceA", Map.of("color", "purple"));
		endpoints("namespaceB", "serviceA", Map.of("color", "red"));
		endpoints("namespaceC", "serviceA", Map.of("color", "purple"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespaceA", "namespaceB"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - some namespaces = [namespaceA, namespaceB]
	 *     - labels = {color=purple}
	 *     - filter = #root.metadata.labels.containsKey("number")
	 *
	 *     - serviceA exists in namespaceA with labels = {color=purple}
	 *     - serviceA exists in namespaceB with labels = {color=red}
	 *     - serviceA exists in namespaceC with labels = {color=purple}
	 *
	 *     - we get serviceA from namespaceA as a result
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

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, allNamespaces,
				someNamespaces, true, 60L, false, spelFilter, Set.of(), labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		service("namespaceA", "serviceA", Map.of("color", "purple", "number", "1"));
		service("namespaceB", "serviceA", Map.of("color", "purple", "cycle", "create"));
		service("namespaceC", "serviceA", Map.of("color", "purple", "number", "1"));

		endpoints("namespaceA", "serviceA", Map.of("color", "purple", "number", "1"));
		endpoints("namespaceB", "serviceA", Map.of("color", "purple", "cycle", "create"));
		endpoints("namespaceC", "serviceA", Map.of("color", "purple", "number", "1"));

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespaceA", "namespaceB"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	private void service(String namespace, String name, Map<String, String> labels) {
		Service service = service(namespace, name, labels, Map.of(), Map.of());
		client.services()
			.inNamespace(namespace)
			.resource(service)
			.create();
	}

	private void endpoints(String namespace, String name, Map<String, String> labels) {
		Endpoints endpoints = endpoints(namespace, name, labels, Map.of());
		client.endpoints()
			.inNamespace(namespace)
			.resource(endpoints)
			.create();
	}

	private static Environment mockEnvironment() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("spring.cloud.kubernetes.client.namespace", "test");
		return environment;
	}

}
