/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.HashMap;
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
	public void testMapperSimple() {
		KubernetesLoadBalancerProperties properties = new KubernetesLoadBalancerProperties();
		Service service = buildService("test", "abc", 8080, null, new HashMap<>());
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
		Service service = buildService("test", "abc", ports, new HashMap<>());
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
		Service service = buildService("test", "abc", 443, null, new HashMap<>());
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
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, true, true, 60,
				false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0);
		List<ServicePort> ports = new ArrayList<>();
		ports.add(new ServicePortBuilder().withPort(443).build());
		Service service = buildService("test", "abc", ports, null, null);
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
		HashMap<String, String> labels = new HashMap<>();
		labels.put("secured", "true");
		labels.put("label1", "123");
		Service service = buildService("test", "abc", 8080, null, labels);
		KubernetesServiceInstance instance = new Fabric8ServiceInstanceMapper(properties,
				KubernetesDiscoveryProperties.DEFAULT).map(service);
		Assertions.assertNotNull(instance);
		Assertions.assertEquals("test", instance.getServiceId());
		Assertions.assertEquals("abc", instance.getInstanceId());
		Assertions.assertTrue(instance.isSecure());
		Assertions.assertEquals(2, instance.getMetadata().keySet().size());
	}

	private Service buildService(String name, String uid, int port, String portName, Map<String, String> labels) {
		ServicePort servicePort = new ServicePortBuilder().withPort(port).withName(portName).build();
		return buildService(name, uid, Collections.singletonList(servicePort), labels);
	}

	private Service buildService(String name, String uid, List<ServicePort> ports, Map<String, String> labels,
			Map<String, String> annotations) {
		return new ServiceBuilder().withNewMetadata().withName(name).withNewUid(uid).addToLabels(labels)
				.withAnnotations(annotations).endMetadata().withNewSpec().addAllToPorts(ports).endSpec().build();
	}

	private Service buildService(String name, String uid, List<ServicePort> ports, Map<String, String> labels) {
		return buildService(name, uid, ports, labels, new HashMap<>(0));
	}

}
