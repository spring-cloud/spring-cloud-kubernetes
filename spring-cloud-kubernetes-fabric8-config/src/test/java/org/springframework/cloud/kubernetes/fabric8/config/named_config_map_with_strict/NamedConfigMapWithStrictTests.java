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

package org.springframework.cloud.kubernetes.fabric8.config.named_config_map_with_strict;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Collections;
import java.util.Map;

/**
 * @author wind57
 */
abstract class NamedConfigMapWithStrictTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	static void setUpBeforeClass(KubernetesClient mockClient) {
		NamedConfigMapWithStrictTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Map<String, String> one = Collections.singletonMap("one.property", "one");
		Map<String, String> oneFromDevProfile = Collections.singletonMap("one.property", "one-from-dev");
		Map<String, String> oneFromUsWest = Collections.singletonMap("one.property", "one-from-us-west");

		createConfigmap("one", one);
		createConfigmap("one-dev", oneFromDevProfile);
		createConfigmap("one-us-west", oneFromUsWest);

		Map<String, String> twoA = Collections.singletonMap("two.property", "two-from-a");
		Map<String, String> twoB = Collections.singletonMap("two.property", "two-from-b");
		Map<String, String> twoC = Collections.singletonMap("two.property", "two-from-c");
		Map<String, String> twoD = Collections.singletonMap("two.property", "two-from-d");

		createConfigmap("two-a", twoA);
		createConfigmap("two-b", twoB);
		createConfigmap("two-c", twoC);
		createConfigmap("two-d", twoD);

	}

	static void createConfigmap(String name, Map<String, String> data) {
		mockClient.configMaps().inNamespace("spring-k8s")
				.create(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().addToData(data).build());
	}

	/**
	 * <pre>
	 *  spring:
	 *   cloud:
	 *     kubernetes:
	 *       config:
	 *         sources:
	 *           - name: one
	 *             strict: true
	 *             namespace: spring-k8s
	 *             include-profile-specific-sources: true
	 *             strict-for-profiles:
	 *               - dev
	 *
	 *  environment active profiles: us-west, dev. Order here is important, as we will get the result
	 *  from "one-dev"
	 * </pre>
	 */
	@Test
	void testOne() {
		this.webClient.get().uri("/named-configmap/strict/one").exchange().expectStatus().isOk()
			.expectBody(String.class).value(Matchers.equalTo("one-from-dev"));
	}

	/**
	 * <pre>
	 *   spring:
	 *   cloud:
	 *     kubernetes:
	 *       config:
	 *         sources:
	 *           - name: two
	 *             strict: false
	 *             namespace: spring-k8s
	 *             include-profile-specific-sources: false
	 *             strict-for-profiles:
	 *               - d
	 *               - c
	 *               - b
	 *
	 *   environment active profiles: a, b, c, d. There is a configmap in each of them.
	 *   The result is is the property from "b", and also the fact that "two" does not exist
	 *   in "spring-k8s" namespace, is not a failure
	 * </pre>
	 */
	@Test
	void testTwo() {
		this.webClient.get().uri("/named-configmap/strict/two").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("two-from-b"));
	}

}
