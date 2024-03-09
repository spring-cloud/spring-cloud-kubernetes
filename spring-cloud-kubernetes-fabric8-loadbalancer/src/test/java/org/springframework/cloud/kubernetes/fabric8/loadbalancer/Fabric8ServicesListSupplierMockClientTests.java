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
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
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
	void testAllNamespaces() {

		createService("a", "service-a", 8887);
		createService("b", "service-b", 8888);

		Environment environment = new MockEnvironment();
		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();

		KubernetesLoadBalancerProperties loadBalancerProperties = new KubernetesLoadBalancerProperties();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
			namespaces, true, 60, false, null, Set.of(), Map.of(), null,
			KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		Fabric8ServicesListSupplier supplier = new Fabric8ServicesListSupplier(
			environment, mockClient, new Fabric8ServiceInstanceMapper(loadBalancerProperties, discoveryProperties),
			discoveryProperties
		);

		List<List<ServiceInstance>> serviceInstances = supplier.get().collectList().block();
		Assertions.assertEquals(serviceInstances.size(), 1);
		List<ServiceInstance> inner = serviceInstances.get(0);
		Assertions.assertEquals(inner.size(), 2);

	}

	private void createService(String namespace, String name, int port) {
		Service service = new ServiceBuilder().withNewMetadata().withNamespace(namespace)
			.withName(name).endMetadata().withSpec(new ServiceSpecBuilder().withPorts(new ServicePortBuilder()
				.withName("http").withPort(port).build()).build()).build();
		mockClient.services().resource(service).create();
	}

}
