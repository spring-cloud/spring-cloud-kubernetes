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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.cloud.kubernetes.discovery.all-namespaces=true")
@EnableKubernetesMockClient(crud = true, https = false)
class LoadBalancerAllNamespacesTests {

	@Autowired
	RestTemplate restTemplate;

	@LocalServerPort
	int randomServerPort;

	static KubernetesClient client;

	@BeforeAll
	static void setup() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, client.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
	}

	@Test
	void testLoadBalancerDifferentNamespace() {
		createTestData();
		String response = restTemplate.getForObject("http://service-b/greeting", String.class);
		Assertions.assertNotNull(response);
		Assertions.assertEquals("greeting", response);
	}

	private void createTestData() {

		Service service = new ServiceBuilder().withNewMetadata().withName("service-b").withNamespace("b").endMetadata()
				.withSpec(new ServiceSpecBuilder()
						.withPorts(new ServicePortBuilder().withProtocol("TCP").withPort(randomServerPort).build())
						.build())
				.build();

		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withName("service-b").withNamespace("b")
				.endMetadata().addNewSubset().addNewAddress().withIp("localhost").endAddress().addNewPort()
				.withName("http").withPort(randomServerPort).endPort().endSubset().build();

		client.endpoints().inNamespace("b").create(endpoints);
		client.services().inNamespace("b").create(service);

	}

}
