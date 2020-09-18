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

package org.springframework.cloud.kubernetes.config;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example3.MultiSecretsApp;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author Haytham Mohamed
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MultiSecretsApp.class,
		properties = { "spring.cloud.bootstrap.name=multiple-secrets" })
@AutoConfigureWebTestClient
public class MultipleSecretsTests {

	private static final String DEFAULT_NAMESPACE = "ns1";

	private static final String ANOTHER_NAMESPACE = "ns2";

	private static final String SECRET_VALUE_1 = "secretValue-1";

	private static final String SECRET_VALUE_2 = "secretValue-2";

	@ClassRule
	public static KubernetesServer server = new KubernetesServer(false, true);

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	@BeforeClass
	public static void setUpBeforeClass() {
		KubernetesClient mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, DEFAULT_NAMESPACE);
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> metadata1 = new HashMap() {
			{
				put("env", "env1");
				put("version", "1.0");
			}
		};

		Secret secret1 = new SecretBuilder().withNewMetadata().withName("name1").withLabels(metadata1).endMetadata()
				.addToData("secrets.secret1", Base64.getEncoder().encodeToString(SECRET_VALUE_1.getBytes())).build();

		mockClient.secrets().inNamespace(DEFAULT_NAMESPACE).create(secret1);

		Map<String, String> metadata2 = new HashMap() {
			{
				put("env", "env2");
				put("version", "2.0");
			}
		};

		Secret secret2 = new SecretBuilder().withNewMetadata().withName("name2").withLabels(metadata2).endMetadata()
				.addToData("secrets.secret2", Base64.getEncoder().encodeToString(SECRET_VALUE_2.getBytes())).build();

		mockClient.secrets().inNamespace(ANOTHER_NAMESPACE).create(secret2);
	}

	@Test
	public void testSecret1() {
		assertResponse("/secret1", SECRET_VALUE_1);
	}

	@Test
	public void testSecret2() {
		assertResponse("/secret2", SECRET_VALUE_2);
	}

	private void assertResponse(String path, String expectedMessage) {
		this.webClient.get().uri(path).exchange().expectStatus().isOk().expectBody().jsonPath("secret")
				.isEqualTo(expectedMessage);
	}

}
