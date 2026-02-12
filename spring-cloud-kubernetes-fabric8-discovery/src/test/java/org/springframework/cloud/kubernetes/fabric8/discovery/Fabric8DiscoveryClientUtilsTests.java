/*
 * Copyright 2013-present the original author or authors.
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
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
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

import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientUtils.endpointSubsetsPortData;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryClientUtilsTests extends Fabric8DiscoveryClientBase {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.services().inAnyNamespace().delete();
		client.endpoints().inAnyNamespace().delete();
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - serviceA present in namespace "A"
	 *     - serviceA present in namespace "B"
	 *     - no filters are applied, so both are present
	 * </pre>
	 */
	@Test
	void testServicesAllNamespacesNoFilters() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false,
				false, null);

		createService("serviceA", "A", Map.of());
		createService("serviceA", "B", Map.of());

		createEndpoints("serviceA", "A", Map.of());
		createEndpoints("serviceA", "B", Map.of());

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(s -> s.getMetadata().get("k8s_namespace")).sorted().toList())
			.containsExactlyInAnyOrder("A", "B");
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
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "",
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		createService("serviceA", "A", Map.of());
		createService("serviceA", "B", Map.of());
		createService("serviceA", "C", Map.of());

		createEndpoints("serviceA", "A", Map.of());
		createEndpoints("serviceA", "B", Map.of());
		createEndpoints("serviceA", "C", Map.of());

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("A", "B"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.containsExactlyInAnyOrder("A", "B");
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
				true, 60L, false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false,
				false, null);

		createService("serviceA", "A", Map.of());
		createService("serviceA", "B", Map.of());
		createService("serviceA", "C", Map.of());

		createEndpoints("serviceA", "A", Map.of());
		createEndpoints("serviceA", "B", Map.of());
		createEndpoints("serviceA", "C", Map.of());

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("A"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceA");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("A");
	}

	@Test
	void testPortsDataOne() {
		List<EndpointSubset> endpointSubsets = List.of(
				new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8081).withName("").build())
					.build(),
				new EndpointSubsetBuilder()
					.withPorts(new EndpointPortBuilder().withPort(8080).withName("https").build())
					.build());

		Map<String, Integer> portsData = endpointSubsetsPortData(endpointSubsets);
		Assertions.assertThat(portsData.size()).isEqualTo(2);
		Assertions.assertThat(portsData.get("https")).isEqualTo(8080);
		Assertions.assertThat(portsData.get("<unset>")).isEqualTo(8081);
	}

	@Test
	void testPortsDataTwo() {
		List<EndpointSubset> endpointSubsets = List.of(
				new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8081).withName("http").build())
					.build(),
				new EndpointSubsetBuilder()
					.withPorts(new EndpointPortBuilder().withPort(8080).withName("https").build())
					.build());

		Map<String, Integer> portsData = endpointSubsetsPortData(endpointSubsets);
		Assertions.assertThat(portsData.size()).isEqualTo(2);
		Assertions.assertThat(portsData.get("https")).isEqualTo(8080);
		Assertions.assertThat(portsData.get("http")).isEqualTo(8081);
	}

	@Test
	void endpointSubsetPortsDataWithoutPorts() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertThat(result).isEmpty();
	}

	@Test
	void endpointSubsetPortsDataSinglePort() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
			.withPorts(new EndpointPortBuilder().withName("name").withPort(80).build())
			.build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get("name")).isEqualTo(80);
	}

	@Test
	void endpointSubsetPortsDataSinglePortNoName() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
			.withPorts(new EndpointPortBuilder().withPort(80).build())
			.build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get("<unset>")).isEqualTo(80);
	}

	/**
	 * <pre>
	 *      - ready addresses are empty
	 *      - not ready addresses are not included
	 * </pre>
	 */
	@Test
	void testEmptyAddresses() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false, null);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		List<EndpointAddress> addresses = Fabric8DiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertThat(addresses).isEmpty();
	}

	/**
	 * <pre>
	 *      - ready addresses has two entries
	 *      - not ready addresses are not included
	 * </pre>
	 */
	@Test
	void testReadyAddressesOnly() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false, null);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
			.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
					new EndpointAddressBuilder().withHostname("two").build())
			.build();
		List<EndpointAddress> addresses = Fabric8DiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertThat(addresses.size()).isEqualTo(2);
	}

	/**
	 * <pre>
	 *      - ready addresses has two entries
	 *      - not ready addresses has a single entry, but we do not take it
	 * </pre>
	 */
	@Test
	void testReadyAddressesTakenNotReadyAddressesNotTaken() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false, null);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
			.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
					new EndpointAddressBuilder().withHostname("two").build())
			.withNotReadyAddresses(new EndpointAddressBuilder().withHostname("three").build())
			.build();
		List<EndpointAddress> addresses = Fabric8DiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertThat(addresses.size()).isEqualTo(2);
		List<String> hostNames = addresses.stream().map(EndpointAddress::getHostname).sorted().toList();
		Assertions.assertThat(hostNames).containsExactlyInAnyOrder("one", "two");
	}

	/**
	 * <pre>
	 *      - ready addresses has two entries
	 *      - not ready addresses has a single entry, but we do not take it
	 * </pre>
	 */
	@Test
	void testBothAddressesTaken() {
		boolean includeNotReadyAddresses = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false, null);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
			.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
					new EndpointAddressBuilder().withHostname("two").build())
			.withNotReadyAddresses(new EndpointAddressBuilder().withHostname("three").build())
			.build();
		List<EndpointAddress> addresses = Fabric8DiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertThat(addresses.size()).isEqualTo(3);
		List<String> hostNames = addresses.stream().map(EndpointAddress::getHostname).sorted().toList();
		Assertions.assertThat(hostNames).containsExactlyInAnyOrder("one", "three", "two");
	}

	private void createEndpoints(String name, String namespace, Map<String, String> labels) {
		Endpoints endpoints = endpoints(namespace, name, labels, Map.of());
		client.endpoints().inNamespace(namespace).resource(endpoints).create();
	}

	private void createService(String name, String namespace, Map<String, String> labels) {
		Service service = service(namespace, name, labels, Map.of(), Map.of());
		client.services().inNamespace(namespace).resource(service).create();
	}

}
