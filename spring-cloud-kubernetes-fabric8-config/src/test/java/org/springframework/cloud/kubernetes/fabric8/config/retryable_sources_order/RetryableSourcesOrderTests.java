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

package org.springframework.cloud.kubernetes.fabric8.config.retryable_sources_order;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

abstract class RetryableSourcesOrderTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		RetryableSourcesOrderTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> secretData = new HashMap<>();
		secretData.put("my.key", Base64.getEncoder().encodeToString("from-secret".getBytes(StandardCharsets.UTF_8)));
		secretData.put("my.one", Base64.getEncoder().encodeToString("one".getBytes(StandardCharsets.UTF_8)));
		createSecret("my-secret", secretData);

		Map<String, String> configMapData = new HashMap<>();
		configMapData.put("my.key", "from-configmap");
		configMapData.put("my.two", "two");
		createConfigmap("my-configmap", configMapData);

	}

	private static void createSecret(String name, Map<String, String> data) {
		mockClient.secrets().inNamespace("spring-k8s")
				.create(new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build());
	}

	private static void createConfigmap(String name, Map<String, String> data) {
		mockClient.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build());
	}

	/**
	 * <pre>
	 *	 1. There is one secret deployed: my-secret. It has two properties: {my.one=one, my.key=from-secret}
	 *	 2. There is one configmap deployed: my-configmap. It has two properties: {my.two=two, my.key=from-configmap}
	 *
	 *	 We invoke three endpoints: /one, /two, /key.
	 *	 The first two prove that both the secret and configmap have been read, the last one proves that
	 *	 config maps have a higher precedence.
	 * </pre>
	 */
	@Test
	void test() {
		this.webClient.get().uri("/retryable-one").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("one"));
		this.webClient.get().uri("/retryable-two").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("two"));

		this.webClient.get().uri("/retryable-key").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("from-configmap"));
	}

}
