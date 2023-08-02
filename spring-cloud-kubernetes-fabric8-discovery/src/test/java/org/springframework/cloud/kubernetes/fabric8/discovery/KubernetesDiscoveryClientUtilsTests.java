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

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;

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
	 *     - properties do not have primary-port-name set
	 *     - service labels do not have primary-port-name set
	 *
	 *     As such null is returned.
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameNotFound(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNull(result);
		Assertions.assertTrue(output.getOut().contains(
				"did not find a primary-port-name in neither properties nor service labels for service with ID : abc"));
	}

	/**
	 * <pre>
	 *     - properties do have primary-port-name set to "https"
	 *     - service labels do not have primary-port-name set
	 *
	 *     As such "https" is returned.
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameFoundInProperties(CapturedOutput output) {
		String primaryPortName = "https";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, primaryPortName);
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : https for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - properties do not have primary-port-name set
	 *     - service labels do have primary-port-name set to "https"
	 *
	 *     As such "https" is returned.
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameFoundInLabels(CapturedOutput output) {
		Map<String, String> labels = Map.of(PRIMARY_PORT_NAME_LABEL_KEY, "https");
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, "https");
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : https for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - properties do have primary-port-name set to "https"
	 *     - service labels do have primary-port-name set to "http"
	 *
	 *     As such "http" is returned (labels win).
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameFoundInBothPropertiesAndLabels(CapturedOutput output) {
		String primaryPortName = "https";
		Map<String, String> labels = Map.of(PRIMARY_PORT_NAME_LABEL_KEY, "http");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, "http");
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : http for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - EndpointSubset has no ports.
	 * </pre>
	 */
	@Test
	void testEndpointsPortNoPorts(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 0);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(output.getOut().contains("no ports found for service : spring-k8s, will return zero"));
	}

	/**
	 * <pre>
	 *     - EndpointSubset has a single entry in getPorts.
	 * </pre>
	 */
	@Test
	void testEndpointsPortSinglePort(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("http").build()).build();
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8080);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(output.getOut().contains("endpoint ports has a single entry, using port : 8080"));
	}

	/**
	 * <pre>
	 *     - primary-port-name is null.
	 * </pre>
	 */
	@Test
	void testEndpointsPortNullPrimaryPortName(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).build(),
						new EndpointPortBuilder().withPort(8081).build())
				.build();
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8080);
		Assertions.assertNull(portData.portName());
		Assertions.assertTrue(output.getOut().contains(
				"did not find a primary-port-name in neither properties nor service labels for service with ID : spring-k8s"));
		Assertions.assertTrue(output.getOut()
				.contains("not found primary-port-name (with value: 'null') via properties or service labels"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'https' to match port"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'http' to match port"));
		Assertions.assertTrue(output.getOut().contains("""
				Make sure that either the primary-port-name label has been added to the service,
				or spring.cloud.kubernetes.discovery.primary-port-name has been configured.
				Alternatively name the primary port 'https' or 'http'
				An incorrect configuration may result in non-deterministic behaviour."""));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "three", such a port name does not exist.
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortNameIsPresentButNotFound(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8080);
		Assertions.assertEquals(portData.portName(), "one");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut()
				.contains("not found primary-port-name (with value: 'three') via properties or service labels"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'https' to match port"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'http' to match port"));
		Assertions.assertTrue(output.getOut().contains("""
				Make sure that either the primary-port-name label has been added to the service,
				or spring.cloud.kubernetes.discovery.primary-port-name has been configured.
				Alternatively name the primary port 'https' or 'http'
				An incorrect configuration may result in non-deterministic behaviour."""));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "two", such a port name exists and matches 8081
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortNameFound(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "two";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8081);
		Assertions.assertEquals(portData.portName(), "two");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : two for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"found primary-port-name (with value: 'two') via properties or service labels to match port : 8081"));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "three", such a port name does not exist.
	 *     - https port exists and this one is returned
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortHttps(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build(),
						new EndpointPortBuilder().withPort(8082).withName("https").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8082);
		Assertions.assertEquals(portData.portName(), "https");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"not found primary-port-name (with value: 'three') via properties or service labels to match port"));
		Assertions.assertTrue(output.getOut().contains("found primary-port-name via 'https' to match port : 8082"));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "three", such a port name does not exist.
	 *     - http port exists and this one is returned
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortHttp(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build(),
						new EndpointPortBuilder().withPort(8082).withName("http").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8082);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"not found primary-port-name (with value: 'three') via properties or service labels to match port"));
		Assertions.assertTrue(output.getOut().contains("found primary-port-name via 'http' to match port : 8082"));
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

		Fabric8ServicePortData portData = new Fabric8ServicePortData(8080, "http");
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

		Fabric8ServicePortData portData = new Fabric8ServicePortData(-1, "http");
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

		Fabric8ServicePortData portData = new Fabric8ServicePortData(0, "http");
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

}
