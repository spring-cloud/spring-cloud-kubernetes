/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.example.App;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.hamcrest.Matchers.containsString;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoint.health.show-details=always" })
@EnableKubernetesMockClient(crud = true, https = false)
public class HealthIndicatorTest {

	private static KubernetesClient mockClient;

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private WebTestClient webClient;

	@BeforeAll
	public static void setUpBeforeClass() {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@Test
	public void healthEndpointShouldContainKubernetes() {
		this.webClient.get().uri("http://localhost:{port}/actuator/health", this.port)
				.accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(String.class)
				.value(containsString("kubernetes"));
	}

}
