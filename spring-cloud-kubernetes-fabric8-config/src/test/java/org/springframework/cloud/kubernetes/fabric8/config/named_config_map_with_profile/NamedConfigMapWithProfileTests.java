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

package org.springframework.cloud.kubernetes.fabric8.config.named_config_map_with_profile;

import java.util.Collections;
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
abstract class NamedConfigMapWithProfileTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		NamedConfigMapWithProfileTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// the fact that property names are the same, also tests that we evaluate config
		// map
		// properties in order: first non-profile ones and then profile based one
		Map<String, String> one = Collections.singletonMap("one.property", "one");
		Map<String, String> oneFromKubernetesProfile = Collections.singletonMap("one.property", "one-from-k8s");

		createConfigmap("configmap-one", one);
		createConfigmap("configmap-one-k8s", oneFromKubernetesProfile);

		Map<String, String> two = Collections.singletonMap("property", "two");
		Map<String, String> twoFromKubernetesProfile = Collections.singletonMap("property", "two-from-k8s");

		createConfigmap("configmap-two", two);
		createConfigmap("configmap-two-k8s", twoFromKubernetesProfile);

		Map<String, String> three = Collections.singletonMap("property", "three");
		Map<String, String> threeFromKubernetesProfile = Collections.singletonMap("property", "three-from-k8s");

		createConfigmap("configmap-three", three);
		createConfigmap("configmap-three-k8s", threeFromKubernetesProfile);

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
	 *   'spring.cloud.kubernetes.config.sources[0].includeProfileSpecificSources=true'
	 * 	 ("one.property", "one-from-k8s")
	 *
	 * 	 As such: @ConfigurationProperties("one"), value is overridden by the one that we read from
	 * 	 the profile based source.
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("/named-configmap/profile/one").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("one-from-k8s"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[1].explicitPrefix=two'
	 *   'spring.cloud.kubernetes.config.sources[1].includeProfileSpecificSources=false'
	 * 	 ("property", "two")
	 *
	 * 	 As such: @ConfigurationProperties("two").
	 *
	 * 	 Even if there is a profile based source, we disabled reading it.
	 * </pre>
	 */
	@Test
	void testTwo() {
		this.webClient.get().uri("/named-configmap/profile/two").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("two"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.config.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.config.sources[2].name=configmap-three'
	 *   'spring.cloud.kubernetes.config.sources[1].includeProfileSpecificSources=true'
	 * 	 ("property", "three")
	 *
	 * 	 As such: @ConfigurationProperties(prefix = "config-three"), value is overridden by the one that we read from
	 * 	 the profile based source
	 * </pre>
	 */
	@Test
	void testThree() {
		this.webClient.get().uri("/named-configmap/profile/three").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("three-from-k8s"));
	}

}
