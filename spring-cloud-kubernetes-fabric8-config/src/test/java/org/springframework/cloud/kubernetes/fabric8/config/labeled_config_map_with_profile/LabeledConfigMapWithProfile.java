/*
 * Copyright 2013-present the original author or authors.
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
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile.properties.Blue;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile.properties.Green;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile.properties.GreenK8s;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile.properties.GreenProd;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile.properties.GreenPurple;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_profile.properties.GreenPurpleK8s;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

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
	private Blue blue;

	@Autowired
	private Green green;

	@Autowired
	private GreenK8s greenK8s;

	@Autowired
	private GreenProd greenProd;

	@Autowired
	private GreenPurple greenPurple;

	@Autowired
	private GreenPurpleK8s greenPurpleK8s;

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
	 *     - configmap with name "green-purple-configmap", labels "{color: green, shape: round}", data: "{eight: 8}"
	 *     - configmap with name "green-purple-configmap-k8s", labels "{color: green}", data: "{eight: eight-ish}"
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
		createConfigMap("color-configmap", colorConfigMap, Collections.singletonMap("color", "blue"));

		// is not taken
		Map<String, String> colorConfigMapK8s = Collections.singletonMap("five", "5");
		createConfigMap("color-configmap-k8s", colorConfigMapK8s, Collections.singletonMap("color", "not-blue"));

		// is found by labels
		Map<String, String> greenConfigMap = Collections.singletonMap("two", "2");
		createConfigMap("green-configmap", greenConfigMap, Collections.singletonMap("color", "green"));

		// is taken
		Map<String, String> greenConfigMapK8s = Collections.singletonMap("six", "6");
		createConfigMap("green-configmap-k8s", greenConfigMapK8s, Collections.singletonMap("color", "green-k8s"));

		// is taken
		Map<String, String> greenConfigMapProd = Collections.singletonMap("seven", "7");
		createConfigMap("green-configmap-prod", greenConfigMapProd, Collections.singletonMap("color", "green"));

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
		createConfigMap("green-purple-configmap-k8s", greenPurpleK8s, Map.of("color", "green"));

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
	 *     this one is taken from : "blue.one". We find "color-configmap" by labels, and
	 *     "color-configmap-k8s" exists, but not taken.
	 *     Since "explicitPrefix=blue", we take "blue.one"
	 * </pre>
	 */
	@Test
	void testBlue() {
		assertThat(blue.getOne()).isEqualTo("1");
	}

	/**
	 * found by labels.
	 */
	@Test
	void testGreen() {
		assertThat(green.getTwo()).isEqualTo("2");
	}

	/**
	 * we find "green-configmap" by labels, and since 'k8s' is an active profile, this one
	 * is taken also (includeProfileSpecificSources=true)
	 */
	@Test
	void testGreenK8s() {
		assertThat(greenK8s.getSix()).isEqualTo("6");
	}

	/**
	 * we find "green-configmap" by labels, and since 'prod' is an active profile, this
	 * one is taken also (includeProfileSpecificSources=true)
	 */
	@Test
	void testGreenProd() {
		assertThat(greenProd.getSeven()).isEqualTo("7");
	}

	/**
	 * found by labels.
	 */
	@Test
	void testGreenPurple() {
		assertThat(greenPurple.getEight()).isEqualTo("8");
	}

	/**
	 * we find "green-configmap" by labels, and since 'prod' is an active profile, this
	 * one is taken also (includeProfileSpecificSources=true)
	 */
	@Test
	void testGreenPurpleK8s() {
		assertThat(greenPurpleK8s.getEight()).isEqualTo("eight-ish");
	}

}
