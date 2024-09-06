/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;

@ExtendWith(OutputCaptureExtension.class)
class Fabric8ServiceInstanceMapperTests {

	@Test
	void testMapperSimple() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		Service service = buildService("test", "test-namespace", "abc", 8080, null, Map.of());
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties,
				KubernetesDiscoveryProperties.DEFAULT)
			.map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
	}

	@Test
	void testMapperMultiplePorts() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		properties.setPortName("http");
		List<ServicePort> ports = new ArrayList<>();
		ports.add(new ServicePortBuilder().withPort(8080).withName("web").build());
		ports.add(new ServicePortBuilder().withPort(9000).withName("http").build());
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of());
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties,
				KubernetesDiscoveryProperties.DEFAULT)
			.map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertEquals(9000, instance.getPort());
	}

	@Test
	void testMapperSecure() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		Service service = buildService("test", "test-namespace", "abc", 443, null, Map.of());

		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(443, 8443), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties, discoveryProperties)
			.map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertTrue(instance.isSecure());
	}

	@Test
	void testMapperSecureNullLabelsAndAnnotations() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, true, Set.of(),
				true, 60, false, null, Set.of(443, 8443), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);
		List<ServicePort> ports = new ArrayList<>();
		ports.add(new ServicePortBuilder().withPort(443).build());
		Service service = buildService("test", "test-namespace", "abc", ports, null, null);
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties, discoveryProperties)
			.map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertTrue(instance.isSecure());
	}

	@Test
	void testMapperSecureWithLabels() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		Map<String, String> labels = Map.of("secured", "true", "label1", "123");
		Service service = buildService("test", "test-namespace", "abc", 8080, null, labels);
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties,
				KubernetesDiscoveryProperties.DEFAULT)
			.map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertTrue(instance.isSecure());
		Assertions.assertEquals(4, instance.getMetadata().keySet().size());
	}

	@Test
	void serviceMetadataTest() {

		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = new ArrayList<>();
		ports.add(new ServicePortBuilder().withPort(443).build());

		Map<String, String> labels = Map.of("one", "1");
		Map<String, String> annotations = Map.of("two", "2");

		Service service = buildService("test", "test-namespace", "abc", ports, labels, annotations);
		Map<String, String> result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.serviceMetadata(service);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result.get("k8s_namespace"), "test-namespace");
		Assertions.assertEquals(result.get("type"), "ClusterIP");
		Assertions.assertEquals(result.get("one"), "1");
		Assertions.assertEquals(result.get("two"), "2");
	}

	/**
	 * <pre>
	 *     service has no ServicePorts
	 * </pre>
	 */
	@Test
	void testMapEmptyPorts(CapturedOutput output) {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = List.of();
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of(), Map.of());
		KubernetesServiceInstance result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.map(service);

		Assertions.assertNull(result);
		Assertions.assertTrue(output.getOut()
			.contains("service : test does not have any ServicePort(s), will not consider it for load balancing"));

	}

	/**
	 * <pre>
	 *     service has a single ServicePort, and its name matches
	 *     'spring.cloud.kubernetes.loadbalancer.portName'
	 * </pre>
	 */
	@Test
	void testSinglePortsMatchesProperty(CapturedOutput output) {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("my-port-name");
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = List.of(new ServicePortBuilder().withPort(8080).withName("my-port-name").build());
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of(), Map.of());
		KubernetesServiceInstance result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.map(service);

		Assertions.assertNotNull(result);
		Assertions.assertTrue(output.getOut()
			.contains(
					"single ServicePort found, will use it as-is (without checking 'spring.cloud.kubernetes.loadbalancer.portName')"));

	}

	/**
	 * <pre>
	 *     service has a single ServicePort, and its name does not match
	 *     'spring.cloud.kubernetes.loadbalancer.portName'.
	 *
	 *     in this case, service is still considered, because we don't care
	 *     about the property name when there is a single service port.
	 * </pre>
	 */
	@Test
	void testSinglePortDoesNotMatchProperty(CapturedOutput output) {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("my-different-port-name");
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = List.of(new ServicePortBuilder().withPort(8080).withName("my-port-name").build());
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of(), Map.of());
		KubernetesServiceInstance result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.map(service);

		Assertions.assertNotNull(result);
		Assertions.assertTrue(output.getOut()
			.contains(
					"single ServicePort found, will use it as-is (without checking 'spring.cloud.kubernetes.loadbalancer.portName')"));

	}

	/**
	 * <pre>
	 *     service has multiple ServicePorts, and 'spring.cloud.kubernetes.loadbalancer.portName' is empty.
	 *     in this case, a single, 'first', port will be returned.
	 * </pre>
	 */
	@Test
	void testMultiplePortsWithoutPortNameProperty(CapturedOutput output) {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("");
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = List.of(new ServicePortBuilder().withPort(8080).withName("one").build(),
				new ServicePortBuilder().withPort(8081).withName("two").build());
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of(), Map.of());
		KubernetesServiceInstance result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.map(service);

		Assertions.assertNotNull(result);
		Assertions.assertTrue(output.getOut().contains("'spring.cloud.kubernetes.loadbalancer.portName' is not set"));
		Assertions.assertTrue(output.getOut().contains("Will return 'first' port found, which is non-deterministic"));

	}

	/**
	 * <pre>
	 *     service has multiple ServicePorts, and 'spring.cloud.kubernetes.loadbalancer.portName' is not empty.
	 * </pre>
	 */
	@Test
	void testMultiplePortsWithPortNamePropertyMatch(CapturedOutput output) {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("one");
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = List.of(new ServicePortBuilder().withPort(8080).withName("one").build(),
				new ServicePortBuilder().withPort(8081).withName("two").build());
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of(), Map.of());
		KubernetesServiceInstance result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.map(service);

		Assertions.assertNotNull(result);
		Assertions.assertTrue(output.getOut().contains("found port name that matches : one"));

	}

	/**
	 * <pre>
	 *     service has multiple ServicePorts, and 'spring.cloud.kubernetes.loadbalancer.portName' is not empty.
	 *     property name also does not match 'potName'
	 * </pre>
	 */
	@Test
	void testMultiplePortsWithPortNamePropertyNoMatch(CapturedOutput output) {
		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		loadBalancerProperties.setPortName("three");
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, false, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);

		List<ServicePort> ports = List.of(new ServicePortBuilder().withPort(8080).withName("one").build(),
				new ServicePortBuilder().withPort(8081).withName("two").build());
		Service service = buildService("test", "test-namespace", "abc", ports, Map.of(), Map.of());
		KubernetesServiceInstance result = new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties)
			.map(service);

		Assertions.assertNotNull(result);
		Assertions.assertTrue(output.getOut().contains("Did not find a port name that is equal to the value three"));
		Assertions.assertTrue(output.getOut().contains("Will return 'first' port found, which is non-deterministic"));
		Assertions.assertTrue(result.getPort() == 8081 || result.getPort() == 8080);
	}

	private Service buildService(String name, String namespace, String uid, int port, String portName,
			Map<String, String> labels) {
		ServicePort servicePort = new ServicePortBuilder().withPort(port).withName(portName).build();
		return buildService(name, namespace, uid, Collections.singletonList(servicePort), labels);
	}

	private Service buildService(String name, String namespace, String uid, List<ServicePort> ports,
			Map<String, String> labels, Map<String, String> annotations) {
		return new ServiceBuilder().withNewMetadata()
			.withNamespace(namespace)
			.withName(name)
			.withUid(uid)
			.addToLabels(labels)
			.withAnnotations(annotations)
			.endMetadata()
			.withNewSpec()
			.addAllToPorts(ports)
			.withType("ClusterIP")
			.endSpec()
			.build();
	}

	private Service buildService(String name, String namespace, String uid, List<ServicePort> ports,
			Map<String, String> labels) {
		return buildService(name, namespace, uid, ports, labels, Map.of());
	}

}
