/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.sanitize_secrets;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
class BootstrapFabric8SanitizeEnvEndpointTests {

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true",
					"management.endpoints.web.exposure.include=*", "spring.cloud.bootstrap.name=sanitize" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@Nested
	class DefaultSettingsTest extends Fabric8SecretsSanitize {

		private static KubernetesClient mockClient;

		@Autowired
		private WebTestClient webClient;

		@LocalManagementPort
		private int port;

		@BeforeAll
		static void setUpBeforeClass() {
			setUpBeforeClass(mockClient);
		}

		@Test
		void test() {
			// configmap is sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeConfigMapName'].value")
					.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeSecretName'].value")
					.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/secret", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/configmap", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeConfigMapValue");
		}

	}

	// management.endpoint.env.show-values = NEVER
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true",
					"management.endpoints.web.exposure.include=*", "spring.cloud.bootstrap.name=sanitize",
					"management.endpoint.env.show-values=NEVER" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@Nested
	class ExplicitNever extends Fabric8SecretsSanitize {

		private static KubernetesClient mockClient;

		@Autowired
		private WebTestClient webClient;

		@LocalManagementPort
		private int port;

		@BeforeAll
		static void setUpBeforeClass() {
			setUpBeforeClass(mockClient);
		}

		@Test
		void test() {
			// configmap is sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeConfigMapName'].value")
					.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeSecretName'].value")
					.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/secret", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/configmap", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeConfigMapValue");
		}

	}

	/**
	 * <pre>
	 *     - management.endpoint.env.show-values = ALWAYS
	 *     - spring.cloud.kubernetes.sanitize.secrets = false
	 *
	 *     Sanitizing functions must apply, but we have none registered, as such
	 *     everything is visible in plain text, both from configmaps and secrets.
	 *
	 * </pre>
	 */
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true",
					"management.endpoints.web.exposure.include=*", "spring.cloud.bootstrap.name=sanitize",
					"management.endpoint.env.show-values=ALWAYS", "spring.cloud.kubernetes.sanitize.secrets=false" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@Nested
	class AlwaysWithoutSanitizingFunction extends Fabric8SecretsSanitize {

		private static KubernetesClient mockClient;

		@Autowired
		private WebTestClient webClient;

		@LocalManagementPort
		private int port;

		@BeforeAll
		static void setUpBeforeClass() {
			setUpBeforeClass(mockClient);
		}

		@Test
		void test() {
			// configmap is not sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeConfigMapName'].value")
					.isEqualTo("sanitizeConfigMapValue");

			// secret is not sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeSecretName'].value")
					.isEqualTo("sanitizeSecretValue");

			// secret is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/secret", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/configmap", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeConfigMapValue");
		}

	}

	/**
	 * <pre>
	 *     - management.endpoint.env.show-values = ALWAYS
	 *     - spring.cloud.kubernetes.sanitize.secrets = true
	 *
	 *     Sanitizing functions must apply, and we have one registered, as such
	 *     configmap is visible in plain text, but secrets are sanitized.
	 *
	 * </pre>
	 */
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true",
					"management.endpoints.web.exposure.include=*", "spring.cloud.bootstrap.name=sanitize-two",
					"management.endpoint.env.show-values=ALWAYS", "spring.cloud.kubernetes.sanitize.secrets=true" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@Nested
	class AlwaysWithSanitizingFunction extends Fabric8SecretsSanitize {

		private static KubernetesClient mockClient;

		@Autowired
		private WebTestClient webClient;

		@LocalManagementPort
		private int port;

		@BeforeAll
		static void setUpBeforeClass() {
			setUpBeforeClass(mockClient);
		}

		@Test
		void test() {
			// configmap is not sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeConfigMapName'].value")
					.isEqualTo("sanitizeConfigMapValue");

			// first secret is sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeSecretName'].value")
					.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// second secret is sanitized
			webClient.get().uri("http://localhost:{port}/actuator/env", this.port).accept(MediaType.APPLICATION_JSON)
					.exchange().expectStatus().isOk().expectBody()
					.jsonPath("propertySources.[*].properties.['sanitize.sanitizeSecretNameTwo'].value")
					.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/secret", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get().uri("http://localhost:{port}/configmap", this.port).exchange().expectStatus().isOk()
					.expectBody().jsonPath("$").isEqualTo("sanitizeConfigMapValue");
		}

	}

}
