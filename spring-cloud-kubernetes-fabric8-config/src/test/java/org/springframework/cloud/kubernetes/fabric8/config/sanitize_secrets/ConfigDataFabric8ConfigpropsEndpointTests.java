/*
 * Copyright 2013-present the original author or authors.
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
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
class ConfigDataFabric8ConfigpropsEndpointTests {

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=*",
					"spring.config.import=kubernetes:,classpath:./sanitize.yaml",
					"spring.cloud.kubernetes.secrets.enabled=true" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@AutoConfigureWebTestClient
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
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeConfigMapName")
				.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is sanitized
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeSecretName")
				.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/secret", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/configmap", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeConfigMapValue");
		}

	}

	// management.endpoint.configprops.show-values = NEVER
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=*",
					"management.endpoint.configprops.show-values=NEVER",
					"spring.config.import=kubernetes:,classpath:./sanitize.yaml",
					"spring.cloud.kubernetes.secrets.enabled=true" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@AutoConfigureWebTestClient
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
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeConfigMapName")
				.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is sanitized
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeSecretName")
				.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/secret", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/configmap", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeConfigMapValue");
		}

	}

	/**
	 * <pre>
	 *     - management.endpoint.configprops.show-values = ALWAYS
	 *     - spring.cloud.kubernetes.sanitize.secrets = false
	 *
	 *     Sanitizing functions must apply, but we have none registered, as such
	 *     everything is visible in plain text, both from configmaps and secrets.
	 *
	 * </pre>
	 */
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=*",
					"management.endpoint.configprops.show-values=ALWAYS",
					"spring.cloud.kubernetes.sanitize.secrets=false",
					"spring.config.import=kubernetes:,classpath:./sanitize.yaml",
					"spring.cloud.kubernetes.secrets.enabled=true" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@AutoConfigureWebTestClient
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
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeConfigMapName")
				.isEqualTo("sanitizeConfigMapValue");

			// secret is not sanitized
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeSecretName")
				.isEqualTo("sanitizeSecretValue");

			// secret is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/secret", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/configmap", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeConfigMapValue");
		}

	}

	/**
	 * <pre>
	 *     - management.endpoint.configprops.show-values = ALWAYS
	 *     - spring.cloud.kubernetes.sanitize.secrets = true
	 *
	 *     Sanitizing functions must apply, and we have one registered, as such
	 *     configmap is visible in plain text, but secrets are sanitized.
	 *
	 * </pre>
	 */
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SanitizeApp.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=*",
					"management.endpoint.configprops.show-values=ALWAYS",
					"spring.cloud.kubernetes.sanitize.secrets=true",
					"spring.config.import=kubernetes:,classpath:./sanitize-two.yaml",
					"spring.cloud.kubernetes.secrets.enabled=true" })
	@EnableKubernetesMockClient(crud = true, https = false)
	@AutoConfigureWebTestClient
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
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeConfigMapName")
				.isEqualTo("sanitizeConfigMapValue");

			// first secret is sanitized
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeSecretName")
				.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// second secret is sanitized
			webClient.get()
				.uri("http://localhost:{port}/actuator/configprops", this.port)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("contexts.sanitize.beans.[*].properties.sanitizeSecretNameTwo")
				.isEqualTo(SanitizableData.SANITIZED_VALUE);

			// secret is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/secret", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeSecretValue");

			// configmap is usable from configuration properties
			webClient.get()
				.uri("http://localhost:{port}/configmap", this.port)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$")
				.isEqualTo("sanitizeConfigMapValue");
		}

	}

}
