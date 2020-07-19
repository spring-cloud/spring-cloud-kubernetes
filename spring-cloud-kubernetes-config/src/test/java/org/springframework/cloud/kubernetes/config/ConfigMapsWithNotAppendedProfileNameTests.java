/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.HashMap;

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
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.cloud.kubernetes.config.ConfigMapTestUtil.createConfigmap;

/**
 * @author Andrey Shlykov
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = App.class,
		properties = { "spring.cloud.bootstrap.name=configmap-not-append-profile" })
@ActiveProfiles("production")
@AutoConfigureWebTestClient
public class ConfigMapsWithNotAppendedProfileNameTests {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
				"false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		createConfigmap(server, "without-profile", "test", new HashMap<String, String>() {
			{
				put("bean.greeting", "Hello ConfigMap Without Profile Name, %s!");
			}
		});

		createConfigmap(server, "without-profile-production", "test",
				new HashMap<String, String>() {
					{
						put("bean.greeting", "Hello ConfigMap With Profile Name, %s!");
					}
				});

		createConfigmap(server, "with-profile", "test", new HashMap<String, String>() {
			{
				put("bean.farewell", "Goodbye ConfigMap Without Profile Name, %s!");
			}
		});

		createConfigmap(server, "with-profile-production", "test",
				new HashMap<String, String>() {
					{
						put("bean.farewell", "Goodbye ConfigMap With Profile Name, %s!");
					}
				});
	}

	@Test
	public void testGreetingEndpoint() {
		this.webClient.get().uri("/api/greeting").exchange().expectStatus().isOk()
				.expectBody().jsonPath("content")
				.isEqualTo("Hello ConfigMap Without Profile Name, World!");
	}

	@Test
	public void testFarewellEndpoint() {
		this.webClient.get().uri("/api/farewell").exchange().expectStatus().isOk()
				.expectBody().jsonPath("content")
				.isEqualTo("Goodbye ConfigMap With Profile Name, World!");
	}

}
