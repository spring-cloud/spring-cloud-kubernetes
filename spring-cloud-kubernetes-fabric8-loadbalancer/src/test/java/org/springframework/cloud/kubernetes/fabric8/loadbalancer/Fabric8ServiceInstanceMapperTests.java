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

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;

class Fabric8ServiceInstanceMapperTests {

	@Test
	void testMapperSimple() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		Service service = buildService("test", "test-namespace", "abc", 8080, null, Map.of());
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties,
				KubernetesDiscoveryProperties.DEFAULT).map(service);
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
				KubernetesDiscoveryProperties.DEFAULT).map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertEquals(9000, instance.getPort());
	}

	@Test
	void testMapperSecure() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		Service service = buildService("test", "test-namespace", "abc", 443, null, Map.of());
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties,
				KubernetesDiscoveryProperties.DEFAULT).map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertTrue(instance.isSecure());
	}

	@Test
	void testMapperSecureNullLabelsAndAnnotations() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, true, Set.of(),
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				false, false);
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
				KubernetesDiscoveryProperties.DEFAULT).map(service);
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

	private Service buildService(String name, String namespace, String uid, int port, String portName,
			Map<String, String> labels) {
		ServicePort servicePort = new ServicePortBuilder().withPort(port).withName(portName).build();
		return buildService(name, namespace, uid, Collections.singletonList(servicePort), labels);
	}

	private Service buildService(String name, String namespace, String uid, List<ServicePort> ports,
			Map<String, String> labels, Map<String, String> annotations) {
		return new ServiceBuilder().withNewMetadata().withNamespace(namespace).withName(name).withUid(uid)
				.addToLabels(labels).withAnnotations(annotations).endMetadata().withNewSpec().addAllToPorts(ports)
				.withType("ClusterIP").endSpec().build();
	}

	private Service buildService(String name, String namespace, String uid, List<ServicePort> ports,
			Map<String, String> labels) {
		return buildService(name, namespace, uid, ports, labels, Map.of());
	}

}
