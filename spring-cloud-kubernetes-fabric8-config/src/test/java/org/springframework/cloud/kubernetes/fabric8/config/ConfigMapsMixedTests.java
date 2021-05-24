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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.example.App;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.util.Lists.newArrayList;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "spring.application.name=" + ConfigMapsMixedTests.APPLICATION_NAME,
				"spring.cloud.kubernetes.config.enableApi=true",
				"spring.cloud.kubernetes.config.paths=" + ConfigMapsMixedTests.FILE_NAME_FULL_PATH })
@EnableKubernetesMockClient(crud = true, https = false)
public class ConfigMapsMixedTests {

	protected static final String FILES_ROOT_PATH = "/tmp/scktests";

	protected static final String FILE_NAME = "application-path.yaml";

	protected static final String FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + FILE_NAME;

	protected static final String APPLICATION_NAME = "configmap-mixed-example";

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	@BeforeAll
	public static void setUpBeforeClass() throws IOException {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Files.createDirectories(Paths.get(FILES_ROOT_PATH));
		ConfigMapTestUtil.createFileWithContent(FILE_NAME_FULL_PATH,
				ConfigMapTestUtil.readResourceFile("application-path.yaml"));

		HashMap<String, String> data = new HashMap<>();
		data.put("bean.morning", "Buenos Dias ConfigMap, %s");

		ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(APPLICATION_NAME).endMetadata()
				.addToData(data).build();

		mockClient.configMaps().inNamespace("test").create(configMap);
	}

	@AfterAll
	public static void teardownAfterClass() {
		newArrayList(FILE_NAME_FULL_PATH, FILES_ROOT_PATH).forEach(fn -> {
			try {
				Files.delete(Paths.get(fn));
			}
			catch (IOException ignored) {
			}
		});
	}

	@Test
	public void greetingInputShouldReturnPropertyFromFile() {
		this.webClient.get().uri("/api/greeting").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Hello ConfigMap, World from path");
	}

	@Test
	public void farewellInputShouldReturnPropertyFromFile() {
		this.webClient.get().uri("/api/farewell").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Bye ConfigMap, World from path");
	}

	@Test
	public void morningInputShouldReturnPropertyFromApi() {
		this.webClient.get().uri("/api/morning").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Buenos Dias ConfigMap, World");
	}

}
