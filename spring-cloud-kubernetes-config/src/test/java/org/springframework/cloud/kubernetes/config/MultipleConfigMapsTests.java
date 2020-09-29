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

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
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
import org.springframework.cloud.kubernetes.config.example2.ExampleApp;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author Charles Moulliard
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ExampleApp.class,
		properties = { "spring.cloud.bootstrap.name=multiplecms" })
@AutoConfigureWebTestClient
public class MultipleConfigMapsTests {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> one = new HashMap<>();
		one.put("bean.common-message", "c1");
		one.put("bean.message1", "m1");

		createConfigmap(server, "s1", "defnamespace", one);

		Map<String, String> two = new HashMap<>();
		two.put("bean.common-message", "c2");
		two.put("bean.message2", "m2");

		createConfigmap(server, "defname", "s2", two);

		Map<String, String> three = new HashMap<>();
		three.put("bean.common-message", "c3");
		three.put("bean.message3", "m3");

		createConfigmap(server, "othername", "othernamespace", three);
	}

	private static void createConfigmap(KubernetesServer server, String configMapName, String namespace,
			Map<String, String> data) {

		server.expect().withPath(String.format("/api/v1/namespaces/%s/configmaps/%s", namespace, configMapName))
				.andReturn(200, new ConfigMapBuilder().withNewMetadata().withName(configMapName).endMetadata()
						.addToData(data).build())
				.always();
	}

	// the last confimap defined in 'multiplecms.yml' has the highest priority, so
	// the common property defined in all configmaps is taken from the last one defined
	@Test
	public void testCommonMessage() {
		assertResponse("/common", "c3");
	}

	@Test
	public void testMessage1() {
		assertResponse("/m1", "m1");
	}

	@Test
	public void testMessage2() {
		assertResponse("/m2", "m2");
	}

	@Test
	public void testMessage3() {
		assertResponse("/m3", "m3");
	}

	private void assertResponse(String path, String expectedMessage) {
		this.webClient.get().uri(path).exchange().expectStatus().isOk().expectBody().jsonPath("message")
				.isEqualTo(expectedMessage);
	}

}
