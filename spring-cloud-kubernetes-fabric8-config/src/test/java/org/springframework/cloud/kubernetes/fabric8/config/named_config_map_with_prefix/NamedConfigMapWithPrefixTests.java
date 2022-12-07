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

package org.springframework.cloud.kubernetes.fabric8.config.named_config_map_with_prefix;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 * @author Ryan Baxter
 */
abstract class NamedConfigMapWithPrefixTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		NamedConfigMapWithPrefixTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> one = new HashMap<>();
		one.put("one.property", "one");
		createConfigmap("config-map-one", one);

		Map<String, String> two = new HashMap<>();
		two.put("property", "two");
		createConfigmap("config-map-two", two);

		Map<String, String> three = new HashMap<>();
		three.put("property", "three");
		createConfigmap("config-map-three", three);

	}

	static void createConfigmap(String name, Map<String, String> data) {
		mockClient.configMaps().inNamespace("spring-k8s")
				.resource(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build())
				.create();
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[0].useNameAsPrefix=false'
	 * 	 ("one.property", "one")
	 *
	 * 	 As such: @ConfigurationProperties("one")
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("/named-config-map/prefix/one").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("one"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[1].explicitPrefix=two'
	 * 	 ("property", "two")
	 *
	 * 	 As such: @ConfigurationProperties("two")
	 * </pre>
	 */
	@Test
	void testTwo() {
		this.webClient.get().uri("/named-config-map/prefix/two").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("two"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[2].name=config-map-three'
	 * 	 ("property", "three")
	 *
	 * 	 As such: @ConfigurationProperties(prefix = "config-map-three")
	 * </pre>
	 */
	@Test
	void testThree() {
		this.webClient.get().uri("/named-config-map/prefix/three").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("three"));
	}

}
