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

package org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile;

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
abstract class LabeledConfigMapWithProfileTests {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	/**
	 * <pre>
	 *     - configmap with name "color-configmap", with labels: "{color: blue}" and "explicitPrefix: blue"
	 *     - configmap with name "green-configmap", with labels: "{color: green}" and "explicitPrefix: blue-again"
	 *     - configmap with name "red-configmap", with labels "{color: not-red}" and "useNameAsPrefix: true"
	 *     - configmap with name "yellow-configmap" with labels "{color: not-yellow}" and useNameAsPrefix: true
	 *     - configmap with name "color-configmap-k8s", with labels : "{color: not-blue}"
	 *     - configmap with name "green-configmap-k8s", with labels : "{color: green-k8s}"
	 *     - configmap with name "green-configmap-prod", with labels : "{color: green-prod}"
	 *
	 *     # a test that proves order: first read non-profile based configmaps, thus profile based
	 *     # configmaps override non-profile ones.
	 *     - configmap with name "green-purple-configmap", labels "{color: green, shape: round}", data: "{eight: 8}"
	 *     - configmap with name "green-purple-configmap-k8s", labels "{color: black}", data: "{eight: eight-ish}"
	 * </pre>
	 */
	static void setUpBeforeClass(KubernetesClient mockClient) {
		LabeledConfigMapWithProfileTests.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// is found by labels
		Map<String, String> colorConfigMap = Collections.singletonMap("one", "1");
		createConfigMap("color-configmap", colorConfigMap, Collections.singletonMap("color", "blue"));

		// is not taken, since "profileSpecificSources=false" for the above
		Map<String, String> colorConfigMapK8s = Collections.singletonMap("five", "5");
		createConfigMap("color-configmap-k8s", colorConfigMapK8s, Collections.singletonMap("color", "not-blue"));

		// is found by labels
		Map<String, String> greenConfigMap = Collections.singletonMap("two", "2");
		createConfigMap("green-configmap", greenConfigMap, Collections.singletonMap("color", "green"));

		// is taken because k8s profile is active and "profileSpecificSources=true"
		Map<String, String> greenConfigMapK8s = Collections.singletonMap("six", "6");
		createConfigMap("green-configmap-k8s", greenConfigMapK8s, Collections.singletonMap("color", "green-k8s"));

		// is taken because prod profile is active and "profileSpecificSources=true"
		Map<String, String> greenConfigMapProd = Collections.singletonMap("seven", "7");
		createConfigMap("green-configmap-prod", greenConfigMapProd, Collections.singletonMap("color", "green-prod"));

		// not taken
		Map<String, String> redConfigMap = Collections.singletonMap("three", "3");
		createConfigMap("red-configmap", redConfigMap, Collections.singletonMap("color", "not-red"));

		// not taken
		Map<String, String> yellowConfigMap = Collections.singletonMap("four", "4");
		createConfigMap("yellow-configmap", yellowConfigMap, Collections.singletonMap("color", "not-yellow"));

		// is found by labels
		Map<String, String> greenPurple = Collections.singletonMap("eight", "8");
		createConfigMap("green-purple-configmap", greenPurple, Map.of("color", "green", "shape", "round"));

		// is taken and thus overrides the above
		Map<String, String> greenPurpleK8s = Collections.singletonMap("eight", "eight-ish");
		createConfigMap("green-purple-configmap-k8s", greenPurpleK8s, Map.of("color", "black"));

	}

	private static void createConfigMap(String name, Map<String, String> data, Map<String, String> labels) {
		mockClient.configMaps().inNamespace("spring-k8s").resource(new ConfigMapBuilder().withNewMetadata()
				.withName(name).withLabels(labels).endMetadata().addToData(data).build()).create();
	}

	/**
	 * <pre>
	 *     this one is taken from : "blue.one". We find "color-configmap" by labels, and
	 *     "color-configmap-k8s" exists, but "includeProfileSpecificSources=false", thus not taken.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		this.webClient.get().uri("/labeled-configmap/profile/blue").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("1"));
	}

	/**
	 * <pre>
	 *   this one is taken from : "green-configmap.green-configmap-k8s.green-configmap-prod.green-purple-configmap.green-purple-configmap-k8s".
	 *   We find "green-configmap" by labels, also "green-configmap-k8s" and "green-configmap-prod" exists,
	 *   because "includeProfileSpecificSources=true" is set. Also "green-purple-configmap" and "green-purple-configmap-k8s"
	 * 	 are found.
	 * </pre>
	 */
	@Test
	void testGreen() {
		this.webClient.get().uri("/labeled-configmap/profile/green").exchange().expectStatus().isOk()
				.expectBody(String.class).value(Matchers.equalTo("2#6#7#eight-ish"));
	}

}
