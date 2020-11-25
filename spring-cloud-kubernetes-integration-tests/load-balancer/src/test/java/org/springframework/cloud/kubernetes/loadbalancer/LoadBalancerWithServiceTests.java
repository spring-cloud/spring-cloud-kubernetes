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

package org.springframework.cloud.kubernetes.loadbalancer;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit5.HoverflyExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.HttpBodyConverter.json;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
		"spring.cloud.kubernetes.loadbalancer.enabled=true" })
@ExtendWith(HoverflyExtension.class)
class LoadBalancerWithServiceTests {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancerWithServiceTests.class);

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	KubernetesClient client;

	@BeforeAll
	static void setup() {
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
	}

	@Test
	void testLoadBalancerInServiceMode(Hoverfly hoverfly) {
		LOGGER.info("Master URL: {}", client.getConfiguration().getMasterUrl());
		hoverfly.simulate(
				dsl(service("http://service-a.test.svc.cluster.local:8080").get("/greeting")
						.willReturn(success().body("greeting"))),
				dsl(service(client.getConfiguration().getMasterUrl().replace("/", "").replace("https:", ""))
						.get("/api/v1/namespaces/test/services/service-a")
						.willReturn(success().body(json(buildService("service-a", 8080, "test"))))));
		String response = restTemplate.getForObject("http://service-a/greeting", String.class);
		Assertions.assertNotNull(response);
		Assertions.assertEquals("greeting", response);
	}

	private Service buildService(String name, int port, String namespace) {
		return new ServiceBuilder().withNewMetadata().withName(name).withNamespace(namespace)
				.withLabels(new HashMap<>()).withAnnotations(new HashMap<>()).endMetadata().withNewSpec().addNewPort()
				.withPort(port).endPort().endSpec().build();
	}

}
