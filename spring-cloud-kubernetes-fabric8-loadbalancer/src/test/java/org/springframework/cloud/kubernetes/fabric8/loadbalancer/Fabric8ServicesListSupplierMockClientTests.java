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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class Fabric8ServicesListSupplierMockClientTests {

	private static KubernetesClient mockClient;

	@BeforeAll
	static void setUpBeforeClass() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	void afterEach() {
		mockClient.services().inAnyNamespace().delete();
	}

	@Test
	void testAllNamespaces(CapturedOutput output) {

		createService("a", "service-a", 8887);
		createService("b", "service-b", 8888);
		createService("c", "service-a", 8889);

		Environment environment = new MockEnvironment().withProperty("loadbalancer.client.name", "service-a");
		boolean allNamespaces = true;
		Set<String> selectiveNamespaces = Set.of();

		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		Fabric8ServicesListSupplier supplier = new Fabric8ServicesListSupplier(environment, mockClient,
				new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties), discoveryProperties);

		List<List<ServiceInstance>> serviceInstances = supplier.get().collectList().block();
		Assertions.assertEquals(serviceInstances.size(), 1);

		List<ServiceInstance> serviceInstancesSorted = serviceInstances.get(0)
			.stream()
			.sorted(Comparator.comparing(ServiceInstance::getServiceId))
			.toList();
		Assertions.assertEquals(serviceInstancesSorted.size(), 2);

		Assertions.assertEquals(serviceInstancesSorted.get(0).getServiceId(), "service-a");
		Assertions.assertEquals(serviceInstancesSorted.get(0).getHost(), "service-a.a.svc.cluster.local");
		Assertions.assertEquals(serviceInstancesSorted.get(0).getPort(), 8887);

		Assertions.assertEquals(serviceInstancesSorted.get(1).getServiceId(), "service-a");
		Assertions.assertEquals(serviceInstancesSorted.get(1).getHost(), "service-a.c.svc.cluster.local");
		Assertions.assertEquals(serviceInstancesSorted.get(1).getPort(), 8889);

		Assertions.assertTrue(output.getOut().contains("discovering services in all namespaces"));
	}

	@Test
	void testOneNamespace(CapturedOutput output) {

		createService("a", "service-c", 8887);
		createService("b", "service-b", 8888);
		createService("c", "service-c", 8889);

		Environment environment = new MockEnvironment().withProperty("spring.cloud.kubernetes.client.namespace", "c")
			.withProperty("loadbalancer.client.name", "service-c");
		boolean allNamespaces = false;
		Set<String> selectiveNamespaces = Set.of();

		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		Fabric8ServicesListSupplier supplier = new Fabric8ServicesListSupplier(environment, mockClient,
				new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties), discoveryProperties);

		List<List<ServiceInstance>> serviceInstances = supplier.get().collectList().block();
		Assertions.assertEquals(serviceInstances.size(), 1);
		List<ServiceInstance> inner = serviceInstances.get(0);

		List<ServiceInstance> serviceInstancesSorted = serviceInstances.get(0)
			.stream()
			.sorted(Comparator.comparing(ServiceInstance::getServiceId))
			.toList();
		Assertions.assertEquals(serviceInstancesSorted.size(), 1);
		Assertions.assertEquals(inner.get(0).getServiceId(), "service-c");
		Assertions.assertEquals(inner.get(0).getHost(), "service-c.c.svc.cluster.local");
		Assertions.assertEquals(inner.get(0).getPort(), 8889);

		Assertions.assertTrue(output.getOut().contains("discovering services in namespace : c"));
	}

	@Test
	void testSelectiveNamespaces(CapturedOutput output) {

		createService("a", "my-service", 8887);
		createService("b", "my-service", 8888);
		createService("c", "my-service", 8889);

		Environment environment = new MockEnvironment().withProperty("loadbalancer.client.name", "my-service");
		boolean allNamespaces = false;
		Set<String> selectiveNamespaces = Set.of("a", "b");

		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		Fabric8ServicesListSupplier supplier = new Fabric8ServicesListSupplier(environment, mockClient,
				new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties), discoveryProperties);

		List<List<ServiceInstance>> serviceInstances = supplier.get().collectList().block();
		Assertions.assertEquals(serviceInstances.size(), 1);

		List<ServiceInstance> serviceInstancesSorted = serviceInstances.get(0)
			.stream()
			.sorted(Comparator.comparing(ServiceInstance::getPort))
			.toList();
		Assertions.assertEquals(serviceInstancesSorted.size(), 2);
		Assertions.assertEquals(serviceInstancesSorted.get(0).getServiceId(), "my-service");
		Assertions.assertEquals(serviceInstancesSorted.get(0).getHost(), "my-service.a.svc.cluster.local");
		Assertions.assertEquals(serviceInstancesSorted.get(0).getPort(), 8887);

		Assertions.assertEquals(serviceInstancesSorted.get(1).getServiceId(), "my-service");
		Assertions.assertEquals(serviceInstancesSorted.get(1).getHost(), "my-service.b.svc.cluster.local");
		Assertions.assertEquals(serviceInstancesSorted.get(1).getPort(), 8888);

		Assertions.assertTrue(output.getOut().contains("discovering services in selective namespaces : [a, b]"));
	}

	private void createService(String namespace, String name, int port) {
		Service service = new ServiceBuilder().withNewMetadata()
			.withNamespace(namespace)
			.withName(name)
			.endMetadata()
			.withSpec(
					new ServiceSpecBuilder().withPorts(new ServicePortBuilder().withName("http").withPort(port).build())
						.build())
			.build();
		mockClient.services().resource(service).create();
	}

}
