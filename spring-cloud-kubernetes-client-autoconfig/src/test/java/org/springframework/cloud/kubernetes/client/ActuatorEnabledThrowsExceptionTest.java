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

package org.springframework.cloud.kubernetes.client;

import io.kubernetes.client.openapi.models.V1Pod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.kubernetes.client.example.App;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = { App.class, ActuatorEnabledThrowsExceptionTest.ActuatorConfig.class },
	properties = { "management.endpoint.health.show-details=always",
		"management.endpoint.health.show-components=always", "management.endpoints.web.exposure.include=health",
		"spring.main.cloud-platform=KUBERNETES", "spring.main.allow-bean-definition-overriding=true" })
class ActuatorEnabledThrowsExceptionTest {

	@Autowired
	private ReactiveHealthContributorRegistry registry;

	@Autowired
	private WebTestClient webClient;

	@LocalManagementPort
	private int port;

	@Test
	void test() {
		webClient.get().uri("http://localhost:{port}/actuator/health", port)
			.accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isEqualTo(503).expectBody()
			.jsonPath("components.kubernetes.status").isEqualTo("DOWN");

		Assertions.assertNotNull(registry.getContributor("kubernetes"));
	}

	@TestConfiguration
	static class ActuatorConfig {

		@Bean
		@Primary
		@SuppressWarnings("unchecked")
		PodUtils<V1Pod> throwingPodUtils() {
			PodUtils<V1Pod> podUtils = (PodUtils<V1Pod>) Mockito.mock(PodUtils.class);
			Mockito.when(podUtils.currentPod()).thenThrow(new RuntimeException("just because"));
			return podUtils;
		}

	}

}
