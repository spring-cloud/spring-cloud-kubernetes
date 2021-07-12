/*
 * Copyright 2013-2021 the original author or authors.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.example.App;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * @author wind57
 */
public class Fabric8ActuatorTests {

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class, properties = {
			"management.health.kubernetes.enabled=false", "management.endpoint.health.show-details=always",
			"management.endpoint.health.show-components=always", "management.endpoints.web.exposure.include=health" })
	public class DisabledHealthTest {

		@Autowired
		private ReactiveHealthContributorRegistry registry;

		@Autowired
		private WebTestClient webClient;

		@Value("${local.server.port}")
		private int port;

		@Test
		public void healthEndpointShouldContainKubernetes() {
			this.webClient.get().uri("http://localhost:{port}/actuator/health", this.port)
					.accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(String.class)
					.value(not(containsString("kubernetes")));

			Assertions.assertNull(registry.getContributor("kubernetes"),
					"reactive kubernetes contributor must NOT be present when 'management.health.kubernetes.enabled=false'");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class, properties = {
			"management.health.kubernetes.enabled=true", "management.endpoint.health.show-details=always",
			"management.endpoint.health.show-components=always", "management.endpoints.web.exposure.include=health" })
	public class EnabledHealthTest {

		@Autowired
		private WebTestClient webClient;

		@Autowired
		private ReactiveHealthContributorRegistry registry;

		@Value("${local.server.port}")
		private int port;

		@Test
		public void healthEndpointShouldContainKubernetes() {
			this.webClient.get().uri("http://localhost:{port}/actuator/health", this.port)
					.accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(String.class)
					.value(containsString("kubernetes"));

			Assertions.assertNotNull(registry.getContributor("kubernetes"),
					"reactive kubernetes contributor must be present when 'management.health.kubernetes.enabled=true'");
		}

	}

}
