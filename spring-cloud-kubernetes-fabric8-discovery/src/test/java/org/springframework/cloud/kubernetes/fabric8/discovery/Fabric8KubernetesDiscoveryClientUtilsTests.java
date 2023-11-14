/*
 * Copyright 2013-2023 the original author or authors.
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

import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.endpointSubsetsPortData;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.services;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesDiscoveryClientUtilsTests {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.services().inAnyNamespace().delete();
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - no filters are applied, so both are present
	 * </pre>
	 */
	@Test
	void testServicesAllNamespacesNoFilters() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());

		List<Service> result = services(properties, client, null, x -> true, null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.stream().map(s -> s.getMetadata().getName()).sorted().toList(),
				List.of("serviceA", "serviceB"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - we search only for "serviceA" filter, so only one is returned
	 * </pre>
	 */
	@Test
	void testServicesAllNamespacesNameFilter() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());

		List<Service> result = services(properties, client, null, x -> true, Map.of("metadata.name", "serviceA"),
				"fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - we search with a filter where a label with name "letter" and value "b" is present
	 * </pre>
	 */
	@Test
	void testServicesAllNamespacesPredicateFilter() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of("letter", "a"));
		service("serviceB", "B", Map.of("letter", "b"));

		List<Service> result = services(properties, client, null,
				x -> x.getMetadata().getLabels().equals(Map.of("letter", "b")), null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : [A, B]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - serviceC present in namespace "C"
	 *     - we search in namespaces [A, B], as such two services are returned
	 * </pre>
	 */
	@Test
	void testServicesSelectiveNamespacesNoFilters() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());
		service("serviceC", "C", Map.of());

		List<Service> result = services(properties, client, null, x -> true, null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.stream().map(x -> x.getMetadata().getName()).sorted().toList(),
				List.of("serviceA", "serviceB"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : [A, B]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - serviceC present in namespace "C"
	 *     - we search in namespaces [A, B] with name filter = "serviceA", so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesSelectiveNamespacesNameFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());
		service("serviceC", "C", Map.of());

		List<Service> result = services(properties, client, null, x -> true, Map.of("metadata.name", "serviceA"),
				"fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : [A, B]
	 *     - serviceA present in namespace "A" with labels [letter, a]
	 *     - serviceB present in namespace "B" with labels [letter, b]
	 *     - serviceC present in namespace "C" with labels [letter, c]
	 *     - we search in namespaces [A, B] with predicate filter = [letter, b], so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesSelectiveNamespacesPredicateFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of("letter", "a"));
		service("serviceB", "B", Map.of("letter", "b"));
		service("serviceC", "C", Map.of("letter", "c"));

		List<Service> result = services(properties, client, null,
				x -> x.getMetadata().getLabels().equals(Map.of("letter", "b")), null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : []
	 *     - namespace from kubernetes namespace provider = [A]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - serviceC present in namespace "C"
	 *     - we search in namespaces [A], as such we get one service
	 * </pre>
	 */
	@Test
	void testServicesNamespaceProviderNoFilters() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());
		service("serviceC", "C", Map.of());

		List<Service> result = services(properties, client, namespaceProvider("A"), x -> true, null,
				"fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : []
	 *     - namespace from kubernetes namespace provider = [A]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "A"
	 *     - we search in namespaces [A] with name filter = "serviceA", so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesNamespaceProviderNameFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "A", Map.of());

		List<Service> result = services(properties, client, namespaceProvider("A"), x -> true,
				Map.of("metadata.name", "serviceA"), "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : []
	 *     - namespace from kubernetes namespace provider = [A]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "A"
	 *     - we search in namespaces [A] with predicate filter = [letter, b], so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesNamespaceProviderPredicateFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of("letter", "a"));
		service("serviceB", "A", Map.of("letter", "b"));

		List<Service> result = services(properties, client, namespaceProvider("A"),
				x -> x.getMetadata().getLabels().equals(Map.of("letter", "b")), null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
	}

	@Test
	void testExternalName() {
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring").build())
				.withNewMetadata().withName("external-name-service").and().build();
		client.services().inNamespace("test").resource(service).create();

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);

		List<Service> result = services(properties, client, namespaceProvider("test"),
				x -> x.getSpec().getType().equals("ExternalName"), Map.of(), "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "external-name-service");
	}

	@Test
	void testPortsDataOne() {
		List<EndpointSubset> endpointSubsets = List.of(
				new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8081).withName("").build())
						.build(),
				new EndpointSubsetBuilder()
						.withPorts(new EndpointPortBuilder().withPort(8080).withName("https").build()).build());

		Map<String, Integer> portsData = endpointSubsetsPortData(endpointSubsets);
		Assertions.assertEquals(portsData.size(), 2);
		Assertions.assertEquals(portsData.get("https"), 8080);
		Assertions.assertEquals(portsData.get("<unset>"), 8081);
	}

	@Test
	void testPortsDataTwo() {
		List<EndpointSubset> endpointSubsets = List.of(
				new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8081).withName("http").build())
						.build(),
				new EndpointSubsetBuilder()
						.withPorts(new EndpointPortBuilder().withPort(8080).withName("https").build()).build());

		Map<String, Integer> portsData = endpointSubsetsPortData(endpointSubsets);
		Assertions.assertEquals(portsData.size(), 2);
		Assertions.assertEquals(portsData.get("https"), 8080);
		Assertions.assertEquals(portsData.get("http"), 8081);
	}

	@Test
	void endpointSubsetPortsDataWithoutPorts() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertEquals(result.size(), 0);
	}

	@Test
	void endpointSubsetPortsDataSinglePort() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withName("name").withPort(80).build()).build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get("name"), 80);
	}

	@Test
	void endpointSubsetPortsDataSinglePortNoName() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(80).build()).build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get("<unset>"), 80);
	}

	private void service(String name, String namespace, Map<String, String> labels) {
		Service service = new ServiceBuilder().withNewMetadata().withName(name).withLabels(labels)
				.withNamespace(namespace).and().build();
		client.services().inNamespace(namespace).resource(service).create();
	}

	private KubernetesNamespaceProvider namespaceProvider(String namespace) {
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", namespace);
		return new KubernetesNamespaceProvider(mockEnvironment);
	}

}
