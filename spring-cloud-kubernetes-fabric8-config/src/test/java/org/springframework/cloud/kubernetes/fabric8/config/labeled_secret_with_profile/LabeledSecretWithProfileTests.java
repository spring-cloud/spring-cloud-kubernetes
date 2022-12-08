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

package org.springframework.cloud.kubernetes.fabric8.config.labeled_secret_with_profile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
abstract class LabeledSecretWithProfileTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	/**
	 * <pre>
	 *     - secret with name "color-secret", with labels: "{color: blue}" and "explicitPrefix: blue"
	 *     - secret with name "green-secret", with labels: "{color: green}" and "explicitPrefix: blue-again"
	 *     - secret with name "red-secret", with labels "{color: not-red}" and "useNameAsPrefix: true"
	 *     - secret with name "yellow-secret" with labels "{color: not-yellow}" and useNameAsPrefix: true
	 *     - secret with name "color-secret-k8s", with labels : "{color: not-blue}"
	 *     - secret with name "green-secret-k8s", with labels : "{color: green-k8s}"
	 *     - secret with name "green-secret-prod", with labels : "{color: green-prod}"
	 *
	 *     # a test that proves order: first read non-profile based secrets, thus profile based
	 *     # secrets override non-profile ones.
	 *     - secret with name "green-purple-secret", labels "{color: green, shape: round}", data: "{eight: 8}"
	 *     - secret with name "green-purple-secret-k8s", labels "{color: black}", data: "{eight: eight-ish}"
	 * </pre>
	 */
	static void setUpBeforeClass(KubernetesClient mockClient) {
		LabeledSecretWithProfileTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// is found by labels
		Map<String, String> colorSecret = Collections.singletonMap("one",
				Base64.getEncoder().encodeToString("1".getBytes(StandardCharsets.UTF_8)));
		createSecret("color-secret", colorSecret, Collections.singletonMap("color", "blue"));

		// is not taken, since "profileSpecificSources=false" for the above
		Map<String, String> colorSecretK8s = Collections.singletonMap("five",
				Base64.getEncoder().encodeToString("5".getBytes(StandardCharsets.UTF_8)));
		createSecret("color-secret-k8s", colorSecretK8s, Collections.singletonMap("color", "not-blue"));

		// is found by labels
		Map<String, String> greenSecret = Collections.singletonMap("two",
				Base64.getEncoder().encodeToString("2".getBytes(StandardCharsets.UTF_8)));
		createSecret("green-secret", greenSecret, Collections.singletonMap("color", "green"));

		// is taken because k8s profile is active and "profileSpecificSources=true"
		Map<String, String> shapeSecretK8s = Collections.singletonMap("six",
				Base64.getEncoder().encodeToString("6".getBytes(StandardCharsets.UTF_8)));
		createSecret("green-secret-k8s", shapeSecretK8s, Collections.singletonMap("color", "green-k8s"));

		// // is taken because prod profile is active and "profileSpecificSources=true"
		Map<String, String> shapeSecretProd = Collections.singletonMap("seven",
				Base64.getEncoder().encodeToString("7".getBytes(StandardCharsets.UTF_8)));
		createSecret("green-secret-prod", shapeSecretProd, Collections.singletonMap("color", "green-prod"));

		// not taken
		Map<String, String> redSecret = Collections.singletonMap("three",
				Base64.getEncoder().encodeToString("3".getBytes(StandardCharsets.UTF_8)));
		createSecret("red-secret", redSecret, Collections.singletonMap("color", "not-red"));

		// not taken
		Map<String, String> yellowSecret = Collections.singletonMap("four",
				Base64.getEncoder().encodeToString("4".getBytes(StandardCharsets.UTF_8)));
		createSecret("yellow-secret", yellowSecret, Collections.singletonMap("color", "not-yellow"));

		// is found by labels
		Map<String, String> greenPurple = Collections.singletonMap("eight",
				Base64.getEncoder().encodeToString("8".getBytes(StandardCharsets.UTF_8)));
		createSecret("green-purple-secret", greenPurple, Map.of("color", "green", "shape", "round"));

		// is taken and thus overrides the above
		Map<String, String> greenPurpleK8s = Collections.singletonMap("eight",
				Base64.getEncoder().encodeToString("eight-ish".getBytes(StandardCharsets.UTF_8)));
		createSecret("green-purple-secret-k8s", greenPurpleK8s, Map.of("color", "black"));

	}

	private static void createSecret(String name, Map<String, String> data, Map<String, String> labels) {
		mockClient.secrets().inNamespace("spring-k8s").resource(new SecretBuilder().withNewMetadata().withName(name)
				.withLabels(labels).endMetadata().addToData(data).build()).create();
	}

	/**
	 * <pre>
	 *     this one is taken from : "blue.one". We find "color-secret" by labels, and
	 *     "color-secrets-k8s" exists, but "includeProfileSpecificSources=false", thus not taken.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		this.webClient.get().uri("/labeled-secret/profile/blue").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("1"));
	}

	/**
	 * <pre>
	 *   this one is taken from : "green-purple-secret.green-purple-secret-k8s.green-secret.green-secret-k8s.green-secret-prod".
	 *   We find "green-secret" by labels, also "green-secrets-k8s" and "green-secrets-prod" exists,
	 *   because "includeProfileSpecificSources=true" is set. Also "green-purple-secret" and "green-purple-secret-k8s"
	 * 	 are found.
	 * </pre>
	 */
	@Test
	void testGreen() {
		this.webClient.get().uri("/labeled-secret/profile/green").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("2#6#7#eight-ish"));
	}

}
