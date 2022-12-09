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

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Charles Moulliard
 */
abstract class ConfigMapsTests {

	private static final String APPLICATION_NAME = "configmap-example";

	private static KubernetesClient mockClient;

	@Autowired(required = false)
	private Config config;

	@Autowired
	private WebTestClient webClient;

	public static void setUpBeforeClass(KubernetesClient mockClient) {
		ConfigMapsTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		HashMap<String, String> data = new HashMap<>();
		data.put("bean.greeting", "Hello ConfigMap, %s!");
		mockClient.configMaps().inNamespace("test").resource(new ConfigMapBuilder().withNewMetadata()
				.withName(APPLICATION_NAME).endMetadata().addToData(data).build()).create();
	}

	@Test
	public void testConfig() {
		assertThat(mockClient.getConfiguration().getMasterUrl()).isEqualTo(this.config.getMasterUrl());
		assertThat(mockClient.getNamespace()).isEqualTo(this.config.getNamespace());
	}

	@Test
	public void testConfigMap() {
		ConfigMap configmap = mockClient.configMaps().inNamespace("test").withName(APPLICATION_NAME).get();
		HashMap<String, String> keys = (HashMap<String, String>) configmap.getData();
		assertThat("Hello ConfigMap, %s!").isEqualTo(keys.get("bean.greeting"));
	}

	@Test
	public void testGreetingEndpoint() {
		this.webClient.get().uri("/api/greeting").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Hello ConfigMap, World!");
	}

}
