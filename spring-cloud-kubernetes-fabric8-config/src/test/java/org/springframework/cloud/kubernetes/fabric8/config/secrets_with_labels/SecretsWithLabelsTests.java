/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.secrets_with_labels;

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
abstract class SecretsWithLabelsTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		SecretsWithLabelsTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> two = Collections.singletonMap("secret.property",
				Base64.getEncoder().encodeToString("value".getBytes(StandardCharsets.UTF_8)));
		createSecret("secret-two", two);

		Map<String, String> three = Collections.singletonMap("secret.property",
				Base64.getEncoder().encodeToString("diff-value".getBytes(StandardCharsets.UTF_8)));
		createSecret("secret-three", three);

	}

	private static void createSecret(String name, Map<String, String> data) {
		mockClient.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build());
	}

	/**
	 * <pre>
	 *	 1. We have two secrets in a certain namespace: "secret-two" and "secret-three".
	 *	 2. Both of the above configure the same secret data: "secret.property", but with different
	 *	    values : "value" and "diff-value"
	 *	 3. In our configuration we want to read only "secret-two" (the one that stores "value" inside)
	 *	 4. This test proves that we do not touch "secret-three"
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("secrets/labels/one").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("value"));
	}

}
