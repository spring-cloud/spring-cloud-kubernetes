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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 */
@ActiveProfiles({ "k8s", "prod" })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = LabeledConfigMapWithProfileApp.class, properties = { "spring.main.cloud-platform=KUBERNETES",
				"spring.application.name=labeled-configmap-with-profile" })
abstract class LabeledConfigMapWithProfile {

	private static KubernetesClient mockClient;

	@Autowired
	private WebTestClient webClient;

	/**
	 * a test that proves order: profile based configmaps override non-profile ones.
	 *
	 * <pre>
	 *     - configmap with name "color_configmap", with labels: "{color: blue}" and "explicitPrefix: blue"
	 *     - configmap with name "green_configmap", with labels: "{color: green}" and "explicitPrefix: blue-again"
	 *     - configmap with name "red_configmap", with labels "{color: not-red}" and "useNameAsPrefix: true"
	 *     - configmap with name "yellow_configmap" with labels "{color: not-yellow}" and useNameAsPrefix: true
	 *     - configmap with name "color_configmap-k8s", with labels : "{color: not-blue}"
	 *     - configmap with name "green_configmap-k8s", with labels : "{color: green}"
	 *     - configmap with name "green_configmap-prod", with labels : "{color: green}"
	 *     - configmap with name "green_purple_configmap", labels "{color: green, shape: round}", data: "{eight: 8}"
	 *     - configmap with name "green_purple_configmap-k8s", labels "{color: green}", data: "{eight: eight-ish}"
	 * </pre>
	 */
	static void setUpBeforeClass(KubernetesClient mockClient) {
		LabeledConfigMapWithProfile.mockClient = mockClient;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// is found by labels
		Map<String, String> colorConfigMap = Collections.singletonMap("one", "1");
		createConfigMap("color_configmap", colorConfigMap, Collections.singletonMap("color", "blue"));

		// is not taken, since "profileSpecificSources=false" for the above
		Map<String, String> colorConfigMapK8s = Collections.singletonMap("five", "5");
		createConfigMap("color_configmap-k8s", colorConfigMapK8s, Collections.singletonMap("color", "not-blue"));

		// is found by labels
		Map<String, String> greenConfigMap = Collections.singletonMap("two", "2");
		createConfigMap("green_configmap", greenConfigMap, Collections.singletonMap("color", "green"));

		// is taken because k8s profile is active and "profileSpecificSources=true"
		Map<String, String> greenConfigMapK8s = Collections.singletonMap("six", "6");
		createConfigMap("green_configmap-k8s", greenConfigMapK8s, Collections.singletonMap("color", "green"));

		// is taken because prod profile is active and "profileSpecificSources=true"
		Map<String, String> greenConfigMapProd = Collections.singletonMap("seven", "7");
		createConfigMap("green_configmap-prod", greenConfigMapProd, Collections.singletonMap("color", "green"));

		// not taken
		Map<String, String> redConfigMap = Collections.singletonMap("three", "3");
		createConfigMap("red_configmap", redConfigMap, Collections.singletonMap("color", "not-red"));

		// not taken
		Map<String, String> yellowConfigMap = Collections.singletonMap("four", "4");
		createConfigMap("yellow_configmap", yellowConfigMap, Collections.singletonMap("color", "not-yellow"));

		// is found by labels
		Map<String, String> greenPurple = Collections.singletonMap("eight", "8");
		createConfigMap("green_purple_configmap", greenPurple, Map.of("color", "green", "shape", "round"));

		// is taken and thus overrides the above
		Map<String, String> greenPurpleK8s = Collections.singletonMap("eight", "eight-ish");
		createConfigMap("green_purple_configmap-k8s", greenPurpleK8s, Map.of("color", "green"));

	}

	private static void createConfigMap(String name, Map<String, String> data, Map<String, String> labels) {
		mockClient.configMaps()
			.inNamespace("spring-k8s")
			.resource(new ConfigMapBuilder().withNewMetadata()
				.withName(name)
				.withLabels(labels)
				.endMetadata()
				.addToData(data)
				.build())
			.create();
	}

	/**
	 * <pre>
	 *     this one is taken from : "blue.one". We find "color_configmap" by labels, and
	 *     "color_configmap-k8s" exists, but "includeProfileSpecificSources=false", thus not taken.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		this.webClient.get()
			.uri("/labeled-configmap/profile/blue")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(Matchers.equalTo("1"));
	}

	/**
	 * <pre>
	 *   this one is taken from : "green_configmap.green_configmap-k8s.green_configmap-prod.green_purple_configmap.green_purple_configmap-k8s".
	 *   We find "green_configmap" by labels, also "green_configmap-k8s" and "green_configmap-prod" exists,
	 *   because "includeProfileSpecificSources=true" is set. Also "green_purple_configmap" and "green_purple_configmap-k8s"
	 * 	 are found.
	 * </pre>
	 */
	@Test
	void testGreen() {
		this.webClient.get()
			.uri("/labeled-configmap/profile/green")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(Matchers.equalTo("2#6#7#eight-ish"));
	}

}
