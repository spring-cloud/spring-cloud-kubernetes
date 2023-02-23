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

import java.util.Collections;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class LoadBalancerWithServiceTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerWithServiceTests.class);

	RestTemplate restTemplate;

	int randomServerPort;

	Fabric8ServiceInstanceMapper mapper;

	static KubernetesClient client;

	static void setup() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, client.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");

	}

	public void before() {
		KubernetesServiceInstance instance = new KubernetesServiceInstance("serviceinstance", "service", "localhost",
				randomServerPort, Collections.EMPTY_MAP, false);
		when(mapper.map(any())).thenReturn(instance);
	}

	void testLoadBalancerSameNamespace() {
		createTestData("service-a", "test");
		String response = restTemplate.getForObject("http://service-a/greeting", String.class);
		Assertions.assertNotNull(response);
		Assertions.assertEquals("greeting", response);
	}

	void testLoadBalancerDifferentNamespace() {
		createTestData("service-b", "b");
		Assertions.assertThrows(IllegalStateException.class,
				() -> restTemplate.getForObject("http://service-b/greeting", String.class));
	}

	private void createTestData(String name, String namespace) {
		client.services().inNamespace(namespace).create(new ServiceBuilder().withNewMetadata().withName(name)
				.endMetadata()
				.withSpec(new ServiceSpecBuilder()
						.withPorts(new ServicePortBuilder().withProtocol("TCP").withPort(randomServerPort).build())
						.build())
				.build());
	}

}
