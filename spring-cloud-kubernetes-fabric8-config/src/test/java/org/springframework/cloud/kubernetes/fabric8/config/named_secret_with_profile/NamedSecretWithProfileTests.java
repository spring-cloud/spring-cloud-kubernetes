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

package org.springframework.cloud.kubernetes.fabric8.config.named_secret_with_profile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
abstract class NamedSecretWithProfileTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		NamedSecretWithProfileTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// both "secret-one" and "secret-one-k8s" have the same property "one.property",
		// but since non-profile based sources are used before profile based sources,
		// properties from secret "secret-one-k8s" must be visible in our tests.
		Map<String, String> one = Collections.singletonMap("one.property",
				Base64.getEncoder().encodeToString("one".getBytes(StandardCharsets.UTF_8)));
		Map<String, String> oneFromKubernetesProfile = Collections.singletonMap("one.property",
				Base64.getEncoder().encodeToString("one-from-k8s".getBytes(StandardCharsets.UTF_8)));

		createSecret("secret-one", one);
		createSecret("secret-one-k8s", oneFromKubernetesProfile);

		Map<String, String> two = Collections.singletonMap("property",
				Base64.getEncoder().encodeToString("two".getBytes(StandardCharsets.UTF_8)));
		Map<String, String> twoFromKubernetesProfile = Collections.singletonMap("property",
				Base64.getEncoder().encodeToString("two-from-k8s".getBytes(StandardCharsets.UTF_8)));

		createSecret("secret-two", two);
		createSecret("secret-two-k8s", twoFromKubernetesProfile);

		Map<String, String> three = Collections.singletonMap("property",
				Base64.getEncoder().encodeToString("three".getBytes(StandardCharsets.UTF_8)));
		Map<String, String> threeFromKubernetesProfile = Collections.singletonMap("property",
				Base64.getEncoder().encodeToString("three-from-k8s".getBytes(StandardCharsets.UTF_8)));

		createSecret("secret-three", three);
		createSecret("secret-three-k8s", threeFromKubernetesProfile);

	}

	private static void createSecret(String name, Map<String, String> data) {
		mockClient.secrets().inNamespace("spring-k8s")
				.resource(new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build())
				.create();
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.secrets.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.secrets.sources[0].useNameAsPrefix=false'
	 *   'spring.cloud.kubernetes.secrets.sources[0].includeProfileSpecificSources=true'
	 * 	 ("one.property", "one-from-k8s")
	 *
	 * 	 As such: @ConfigurationProperties("one"), value is overridden by the one that we read from
	 * 	 the profile based source.
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("/named-secret/profile/one").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("one-from-k8s"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.secrets.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.secrets.sources[1].explicitPrefix=two'
	 *   'spring.cloud.kubernetes.secrets.sources[1].includeProfileSpecificSources=false'
	 * 	 ("property", "two")
	 *
	 * 	 As such: @ConfigurationProperties("two").
	 *
	 * 	 Even if there is a profile based source, we disabled reading it.
	 * </pre>
	 */
	@Test
	void testTwo() {
		this.webClient.get().uri("/named-secret/profile/two").exchange().expectStatus().isOk().expectBody(String.class)
				.value(Matchers.equalTo("two"));
	}

	/**
	 * <pre>
	 *   'spring.cloud.kubernetes.secrets.useNameAsPrefix=true'
	 *   'spring.cloud.kubernetes.secrets.sources[2].name=secret-three'
	 *   'spring.cloud.kubernetes.secrets.sources[1].includeProfileSpecificSources=true'
	 * 	 ("property", "three")
	 *
	 * 	 As such: @ConfigurationProperties(prefix = "secret-three"), value is overridden by the one that we read from
	 * 	 * 	 the profile based source
	 * </pre>
	 */
	@Test
	void testThree() {
		this.webClient.get().uri("/named-secret/profile/three").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("three-from-k8s"));
	}

}
