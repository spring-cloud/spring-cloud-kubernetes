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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.models.CoreV1EndpointPortBuilder;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointAddressBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1EndpointSubsetBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.springframework.cloud.kubernetes.client.discovery.KubernetesClientDiscoveryClientUtils.endpointSubsetsPortData;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesClientDiscoveryClientUtilsTests {

	@Test
	void testPortsDataOne() {
		List<V1EndpointSubset> endpointSubsets = List.of(
				new V1EndpointSubsetBuilder()
					.withPorts(new CoreV1EndpointPortBuilder().withPort(8081).withName("").build())
					.build(),
				new V1EndpointSubsetBuilder()
					.withPorts(new CoreV1EndpointPortBuilder().withPort(8080).withName("https").build())
					.build());

		Map<String, Integer> portsData = endpointSubsetsPortData(endpointSubsets);
		Assertions.assertThat(portsData.size()).isEqualTo(2);
		Assertions.assertThat(portsData.get("https")).isEqualTo(8080);
		Assertions.assertThat(portsData.get("<unset>")).isEqualTo(8081);
	}

	@Test
	void testPortsDataTwo() {
		List<V1EndpointSubset> endpointSubsets = List.of(
				new V1EndpointSubsetBuilder()
					.withPorts(new CoreV1EndpointPortBuilder().withPort(8081).withName("http").build())
					.build(),
				new V1EndpointSubsetBuilder()
					.withPorts(new CoreV1EndpointPortBuilder().withPort(8080).withName("https").build())
					.build());

		Map<String, Integer> portsData = endpointSubsetsPortData(endpointSubsets);
		Assertions.assertThat(portsData.size()).isEqualTo(2);
		Assertions.assertThat(portsData.get("https")).isEqualTo(8080);
		Assertions.assertThat(portsData.get("http")).isEqualTo(8081);
	}

	@Test
	void endpointSubsetPortsDataWithoutPorts() {
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder().build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertThat(result).isEmpty();
	}

	@Test
	void endpointSubsetPortsDataSinglePort() {
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder()
			.withPorts(new CoreV1EndpointPortBuilder().withName("name").withPort(80).build())
			.build();
		Map<String, Integer> result = endpointSubsetsPortData(List.of(endpointSubset));

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get("name")).isEqualTo(80);
	}

	@Test
	void endpointSubsetPortsDataSinglePortNoName() {
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder()
			.withPorts(new CoreV1EndpointPortBuilder().withPort(80).build())
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
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder().build();
		List<V1EndpointAddress> addresses = KubernetesClientDiscoveryClientUtils.addresses(endpointSubset, properties);
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
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder()
			.withAddresses(new V1EndpointAddressBuilder().withHostname("one").build(),
					new V1EndpointAddressBuilder().withHostname("two").build())
			.build();
		List<V1EndpointAddress> addresses = KubernetesClientDiscoveryClientUtils.addresses(endpointSubset, properties);
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
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder()
			.withAddresses(new V1EndpointAddressBuilder().withHostname("one").build(),
					new V1EndpointAddressBuilder().withHostname("two").build())
			.withNotReadyAddresses(new V1EndpointAddressBuilder().withHostname("three").build())
			.build();
		List<V1EndpointAddress> addresses = KubernetesClientDiscoveryClientUtils.addresses(endpointSubset, properties);
		List<String> hostNames = addresses.stream().map(V1EndpointAddress::getHostname).sorted().toList();
		Assertions.assertThat(hostNames).containsExactly("one", "two");
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
		V1EndpointSubset endpointSubset = new V1EndpointSubsetBuilder()
			.withAddresses(new V1EndpointAddressBuilder().withHostname("one").build(),
					new V1EndpointAddressBuilder().withHostname("two").build())
			.withNotReadyAddresses(new V1EndpointAddressBuilder().withHostname("three").build())
			.build();
		List<V1EndpointAddress> addresses = KubernetesClientDiscoveryClientUtils.addresses(endpointSubset, properties);
		List<String> hostNames = addresses.stream().map(V1EndpointAddress::getHostname).sorted().toList();
		Assertions.assertThat(hostNames).containsExactly("one", "three", "two");
	}

	@Test
	void emptyLabels() {
		String result = KubernetesClientDiscoveryClientUtils.labelSelector(Map.of());
		Assertions.assertThat(result).isNull();
	}

	@Test
	void singleLabel() {
		String result = KubernetesClientDiscoveryClientUtils.labelSelector(Map.of("a", "b"));
		Assertions.assertThat(result).isEqualTo("a=b");
	}

	@Test
	void multipleLabelsLabel() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("a", "b");
		labels.put("c", "d");
		String result = KubernetesClientDiscoveryClientUtils.labelSelector(labels);
		Assertions.assertThat(result).isEqualTo("a=b,c=d");
	}

}
