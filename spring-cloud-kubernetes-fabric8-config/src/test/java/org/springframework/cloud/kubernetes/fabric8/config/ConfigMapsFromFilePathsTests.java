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

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
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
		properties = { "spring.application.name=configmap-path-example",
				"spring.cloud.kubernetes.config.enableApi=false",
				"spring.cloud.kubernetes.config.paths=" + ConfigMapsFromFilePathsTests.FIRST_FILE_NAME_FULL_PATH + ","
						+ ConfigMapsFromFilePathsTests.SECOND_FILE_NAME_FULL_PATH + ","
						+ ConfigMapsFromFilePathsTests.FIRST_FILE_NAME_DUPLICATED_FULL_PATH,
				"spring.main.cloud-platform=KUBERNETES" })
abstract class ConfigMapsFromFilePathsTests {

	protected static final String FILES_ROOT_PATH = "/tmp/scktests";

	protected static final String FILES_SUB_PATH = "another-directory";

	protected static final String FIRST_FILE_NAME = "application.properties";

	protected static final String SECOND_FILE_NAME = "extra.properties";

	protected static final String UNUSED_FILE_NAME = "unused.properties";

	protected static final String FIRST_FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + FIRST_FILE_NAME;

	protected static final String SECOND_FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + SECOND_FILE_NAME;

	protected static final String UNUSED_FILE_NAME_FULL_PATH = FILES_ROOT_PATH + "/" + UNUSED_FILE_NAME;

	protected static final String FIRST_FILE_NAME_DUPLICATED_FULL_PATH = FILES_ROOT_PATH + "/" + FILES_SUB_PATH + "/"
			+ FIRST_FILE_NAME;

	@Autowired
	private WebTestClient webClient;

	public static void setUpBeforeClass(KubernetesClient mockClient) throws IOException {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
		Files.createDirectories(Paths.get(FILES_ROOT_PATH + "/" + FILES_SUB_PATH));
		ConfigMapTestUtil.createFileWithContent(FIRST_FILE_NAME_FULL_PATH, "bean.greeting=Hello from path!");
		ConfigMapTestUtil.createFileWithContent(SECOND_FILE_NAME_FULL_PATH, "bean.farewell=Bye from path!");
		ConfigMapTestUtil.createFileWithContent(UNUSED_FILE_NAME_FULL_PATH, "bean.morning=Morning from path!");
		ConfigMapTestUtil.createFileWithContent(FIRST_FILE_NAME_DUPLICATED_FULL_PATH,
				"bean.bonjour=Bonjour from path!");
	}

	@AfterAll
	public static void teardownAfterClass() {
		newArrayList(FIRST_FILE_NAME_FULL_PATH, SECOND_FILE_NAME_FULL_PATH, SECOND_FILE_NAME_FULL_PATH, FILES_ROOT_PATH)
				.forEach(fn -> {
					try {
						Files.delete(Paths.get(fn));
					}
					catch (IOException ignored) {
					}
				});
	}

	@Test
	public void greetingInputShouldReturnPropertyFromFirstFile() {
		this.webClient.get().uri("/api/greeting").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Hello from path!");
	}

	@Test
	public void farewellInputShouldReturnPropertyFromSecondFile() {
		this.webClient.get().uri("/api/farewell").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Bye from path!");
	}

	@Test
	public void morningInputShouldReturnDefaultValue() {
		this.webClient.get().uri("/api/morning").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Good morning, World!");
	}

	@Test
	public void bonjourInputShouldReturnPropertyFromDuplicatedFile() {
		this.webClient.get().uri("/api/bonjour").exchange().expectStatus().isOk().expectBody().jsonPath("content")
				.isEqualTo("Bonjour from path!");
	}

}
