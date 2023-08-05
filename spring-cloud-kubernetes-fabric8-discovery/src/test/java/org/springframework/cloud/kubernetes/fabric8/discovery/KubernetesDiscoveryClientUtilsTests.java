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
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesDiscoveryClientUtilsTests {

	@Test
	void testSubsetsFromEndpointsEmptySubsets() {
		Endpoints endpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("non-default").build()).build();
		EndpointSubsetNS result = Fabric8KubernetesDiscoveryClientUtils.subsetsFromEndpoints(endpoints);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.endpointSubset(), List.of());
		Assertions.assertEquals(result.namespace(), "non-default");
	}

	@Test
	void testSubsetsFromEndpointsNonEmptySubsets() {
		Endpoints endpoints = new EndpointsBuilder().withSubsets((List<EndpointSubset>) null)
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").build())
				.withSubsets(
						new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build()).build())
				.build();
		EndpointSubsetNS result = Fabric8KubernetesDiscoveryClientUtils.subsetsFromEndpoints(endpoints);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.endpointSubset().size(), 1);
		Assertions.assertEquals(result.endpointSubset().get(0).getPorts().get(0).getPort(), 8080);
		Assertions.assertEquals(result.namespace(), "default");
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

	@Test
	void testServiceInstance() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false);
		ServicePortSecureResolver resolver = new ServicePortSecureResolver(properties);
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();
		EndpointAddress address = new EndpointAddressBuilder().withNewTargetRef().withUid("123").endTargetRef()
				.withIp("127.0.0.1").build();

		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		ServiceInstance serviceInstance = Fabric8KubernetesDiscoveryClientUtils.serviceInstance(resolver, service,
				address, portData, "my-service", Map.of("a", "b"), "k8s", properties, null);
		Assertions.assertTrue(serviceInstance instanceof DefaultKubernetesServiceInstance);
		DefaultKubernetesServiceInstance defaultInstance = (DefaultKubernetesServiceInstance) serviceInstance;
		Assertions.assertEquals(defaultInstance.getInstanceId(), "123");
		Assertions.assertEquals(defaultInstance.getServiceId(), "my-service");
		Assertions.assertEquals(defaultInstance.getHost(), "127.0.0.1");
		Assertions.assertEquals(defaultInstance.getPort(), 8080);
		Assertions.assertFalse(defaultInstance.isSecure());
		Assertions.assertEquals(defaultInstance.getUri().toASCIIString(), "http://127.0.0.1:8080");
		Assertions.assertEquals(defaultInstance.getMetadata(), Map.of("a", "b"));
		Assertions.assertEquals(defaultInstance.getScheme(), "http");
		Assertions.assertEquals(defaultInstance.getNamespace(), "k8s");
		Assertions.assertNull(defaultInstance.getCluster());
	}

	@Test
	void testExternalNameServiceInstance() {
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withExternalName("spring.io").withType("ExternalName").build())
				.withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(-1, "http");
		ServiceInstance serviceInstance = Fabric8KubernetesDiscoveryClientUtils.serviceInstance(null, service, null,
				portData, "my-service", Map.of("a", "b"), "k8s", KubernetesDiscoveryProperties.DEFAULT, null);
		Assertions.assertTrue(serviceInstance instanceof DefaultKubernetesServiceInstance);
		DefaultKubernetesServiceInstance defaultInstance = (DefaultKubernetesServiceInstance) serviceInstance;
		Assertions.assertEquals(defaultInstance.getInstanceId(), "123");
		Assertions.assertEquals(defaultInstance.getServiceId(), "my-service");
		Assertions.assertEquals(defaultInstance.getHost(), "spring.io");
		Assertions.assertEquals(defaultInstance.getPort(), -1);
		Assertions.assertFalse(defaultInstance.isSecure());
		Assertions.assertEquals(defaultInstance.getUri().toASCIIString(), "spring.io");
		Assertions.assertEquals(defaultInstance.getMetadata(), Map.of("a", "b"));
		Assertions.assertEquals(defaultInstance.getScheme(), "http");
		Assertions.assertEquals(defaultInstance.getNamespace(), "k8s");
		Assertions.assertNull(defaultInstance.getCluster());
	}

	@Test
	void testNoPortsServiceInstance() {
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		EndpointAddress endpointAddress = new EndpointAddressBuilder().withIp("127.0.0.1").build();

		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(0, "http");
		ServiceInstance serviceInstance = Fabric8KubernetesDiscoveryClientUtils.serviceInstance(null, service,
				endpointAddress, portData, "my-service", Map.of("a", "b"), "k8s", KubernetesDiscoveryProperties.DEFAULT,
				null);
		Assertions.assertTrue(serviceInstance instanceof DefaultKubernetesServiceInstance);
		DefaultKubernetesServiceInstance defaultInstance = (DefaultKubernetesServiceInstance) serviceInstance;
		Assertions.assertEquals(defaultInstance.getInstanceId(), "123");
		Assertions.assertEquals(defaultInstance.getServiceId(), "my-service");
		Assertions.assertEquals(defaultInstance.getHost(), "127.0.0.1");
		Assertions.assertEquals(defaultInstance.getScheme(), "http");
		Assertions.assertEquals(defaultInstance.getPort(), 0);
		Assertions.assertFalse(defaultInstance.isSecure());
		Assertions.assertEquals(defaultInstance.getUri().toASCIIString(), "http://127.0.0.1");
		Assertions.assertEquals(defaultInstance.getMetadata(), Map.of("a", "b"));
		Assertions.assertEquals(defaultInstance.getNamespace(), "k8s");
		Assertions.assertNull(defaultInstance.getCluster());
	}

	/**
	 * endpoints ports are empty.
	 */
	@Test
	void testEndpointSubsetPortsDataOne() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		Map<String, Integer> result = Fabric8KubernetesDiscoveryClientUtils.endpointSubsetPortsData(endpointSubset);
		Assertions.assertTrue(result.isEmpty());
	}

	/**
	 * endpoints ports has one entry.
	 */
	@Test
	void testEndpointSubsetPortsDataTwo() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("http").build()).build();
		Map<String, Integer> result = Fabric8KubernetesDiscoveryClientUtils.endpointSubsetPortsData(endpointSubset);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get("http"), 8080);
	}

	/**
	 * endpoints ports has three entries, only two are picked up.
	 */
	@Test
	void testEndpointSubsetPortsDataThree() {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("http").build(),
						new EndpointPortBuilder().withPort(8081).build(),
						new EndpointPortBuilder().withPort(8082).withName("https").build())
				.build();
		Map<String, Integer> result = Fabric8KubernetesDiscoveryClientUtils.endpointSubsetPortsData(endpointSubset);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get("http"), 8080);
		Assertions.assertEquals(result.get("https"), 8082);
	}

}
