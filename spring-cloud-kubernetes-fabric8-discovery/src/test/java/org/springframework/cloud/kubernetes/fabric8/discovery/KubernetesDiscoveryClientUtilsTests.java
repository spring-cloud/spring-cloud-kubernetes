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

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
class KubernetesDiscoveryClientUtilsTests {

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
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 0);
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
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
						new EndpointAddressBuilder().withHostname("two").build())
				.build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 2);
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
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
						new EndpointAddressBuilder().withHostname("two").build())
				.withNotReadyAddresses(new EndpointAddressBuilder().withHostname("three").build()).build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 2);
		List<String> hostNames = addresses.stream().map(EndpointAddress::getHostname).sorted().toList();
		Assertions.assertEquals(hostNames, List.of("one", "two"));
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
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
						new EndpointAddressBuilder().withHostname("two").build())
				.withNotReadyAddresses(new EndpointAddressBuilder().withHostname("three").build()).build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 3);
		List<String> hostNames = addresses.stream().map(EndpointAddress::getHostname).sorted().toList();
		Assertions.assertEquals(hostNames, List.of("one", "three", "two"));
	}

}
