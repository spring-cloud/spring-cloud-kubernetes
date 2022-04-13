/*
 * Copyright 2013-2021 the original author or authors.
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
 */
abstract class ConfigMapWithIncludeProfileSpecificSourcesTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	public static void setUpBeforeClass(KubernetesClient mockClient) {
		ConfigMapWithIncludeProfileSpecificSourcesTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> one = new HashMap<>();
		one.put("one.property", "one");
		createConfigmap("config-map-one-dev", one);

		Map<String, String> two = new HashMap<>();
		two.put("two.property", "two");
		createConfigmap("config-map-two", two);

		Map<String, String> twoDev = new HashMap<>();
		twoDev.put("two.property", "twoDev");
		createConfigmap("config-map-two-dev", twoDev);

		Map<String, String> three = new HashMap<>();
		three.put("three.property", "three");
		createConfigmap("config-map-three", three);

		Map<String, String> threeDev = new HashMap<>();
		threeDev.put("three.property", "threeDev");
		createConfigmap("config-map-three-dev", threeDev);

	}

	private static void createConfigmap(String name, Map<String, String> data) {
		mockClient.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build());
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.includeProfileSpecificSources=false'
	 *   'spring.cloud.kubernetes.config.sources[0].includeProfileSpecificSources=true'
	 *   'spring.cloud.kubernetes.config.sources[0].name=config-map-one'
	 *
	 *   We do not define config-map 'config-map-one', but we do define 'config-map-one-dev'.
	 *
	 * 	 As such: @ConfigurationProperties("one") must be resolved from 'config-map-one-dev'
	 * </pre>
	 */
	@Test
	public void testOne() {
		this.webClient.get().uri("/profile-specific/one").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("one"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.includeProfileSpecificSources=false'
	 *   'spring.cloud.kubernetes.config.sources[1].includeProfileSpecificSources=false'
	 *   'spring.cloud.kubernetes.config.sources[1].name=config-map-two'
	 *
	 *   We define config-map 'config-map-two', but we also define 'config-map-two-dev'.
	 *   This tests proves that data will be read from 'config-map-two' _only_, even if 'config-map-two-dev'
	 *   also exists. This happens because of the 'includeProfileSpecificSources=false' property defined at the source level.
	 *   If this would be incorrect, the value we read from '/profile-specific/two' would have been 'twoDev' and _not_ 'two',
	 *   simply because 'config-map-two-dev' would override the property value.
	 *
	 * 	 As such: @ConfigurationProperties("two") must be resolved from 'config-map-two'
	 * </pre>
	 */
	@Test
	public void testTwo() {
		this.webClient.get().uri("/profile-specific/two").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("two"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.includeProfileSpecificSources=false'
	 *   'spring.cloud.kubernetes.config.sources[2].name=config-map-three'
	 *
	 *   We define config-map 'config-map-three', but we also define 'config-map-three-dev'.
	 *   This tests proves that data will be read from 'config-map-three' _only_, even if 'config-map-three-dev'
	 *   also exists. This happens because the 'includeProfileSpecificSources'  property is not defined at the source level,
	 *   but it is defaulted from the root level, where we set it to false.
	 *   If this would be incorrect, the value we read from '/profile-specific/three' would have been 'threeDev' and _not_ 'three',
	 *   simply because 'config-map-three-dev' would override the property value.
	 *
	 * 	 As such: @ConfigurationProperties("three") must be resolved from 'config-map-three'
	 * </pre>
	 */
	@Test
	public void testThree() {
		this.webClient.get().uri("/profile-specific/three").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("three"));
	}

}
