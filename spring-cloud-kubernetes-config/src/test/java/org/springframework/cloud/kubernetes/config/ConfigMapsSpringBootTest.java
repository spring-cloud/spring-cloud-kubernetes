/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.restassured.RestAssured;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.test.context.junit4.SpringRunner;

import static io.restassured.RestAssured.when;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:cmoullia@redhat.com">Charles Moulliard</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                classes = App.class,
				properties = { "spring.application.name=configmap-example",
	           "spring.cloud.kubernetes.reload.enabled=false"}
	           )
public class ConfigMapsSpringBootTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired(required = false)
	Config config;

	private static final String APPLICATION_NAME = "configmap-example";

	@Value("${local.server.port}")
	private int port;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = server.getClient();

		//Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");

		HashMap<String,String> data = new HashMap<>();
		data.put("bean.greeting","Hello ConfigMap, %s!");
		server.expect().withPath("/api/v1/namespaces/test/configmaps/" + APPLICATION_NAME).andReturn(200, new ConfigMapBuilder()
			.withNewMetadata().withName(APPLICATION_NAME).endMetadata()
			.addToData(data)
			.build())
			.always();
	}


	@Before
	public void setUp() {
		RestAssured.baseURI = String.format("http://localhost:%d/api/greeting", port);
	}

	@Test
	public void testConfig() {
		assertEquals(config.getMasterUrl(),mockClient.getConfiguration().getMasterUrl());
		assertEquals(config.getNamespace(),mockClient.getNamespace());
	}

	@Test
	public void testGreetingEndpoint() {
		when().get()
			.then()
			.statusCode(200)
			.body("content", is("Hello ConfigMap, World!"));
	}

	@Test
	public void testConfigMap() {
		ConfigMap configmap = mockClient.configMaps().inNamespace("test").withName(APPLICATION_NAME).get();
		HashMap<String,String> keys = (HashMap<String, String>) configmap.getData();
		assertEquals(keys.get("bean.greeting"),"Hello ConfigMap, %s!");
	}

}
