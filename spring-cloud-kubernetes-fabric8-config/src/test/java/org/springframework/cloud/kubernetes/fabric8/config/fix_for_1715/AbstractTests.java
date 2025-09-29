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

package org.springframework.cloud.kubernetes.fabric8.config.fix_for_1715;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.fix_for_1715.properties.APropertySourceByLabel;
import org.springframework.cloud.kubernetes.fabric8.config.fix_for_1715.properties.APropertySourceByName;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@ActiveProfiles({ "k8s" })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Fix1715App.class,
		properties = { "spring.application.name=fix-1715", "spring.cloud.kubernetes.reload.enabled=false",
				"spring.main.cloud-platform=KUBERNETES" })
abstract class AbstractTests {

	private static KubernetesClient mockClient;

	@Autowired
	private APropertySourceByName aByName;

	@Autowired
	private APropertySourceByLabel aByLabel;

	/**
	 * this one is simply read by name
	 */
	@Test
	void testAByName() {
		assertThat(aByName.aByName()).isEqualTo("aByName");
	}

	/**
	 * this one is read by name + profile
	 */
	@Test
	void testAByNameAndProfile() {
		assertThat(aByName.aByNameK8s()).isEqualTo("aByNameK8s");
	}

	/**
	 * this one is simply read by name
	 */
	@Test
	void testAByLabel() {
		assertThat(aByLabel.aByLabel()).isEqualTo("aByLabel");
	}

	/**
	 * This one is not read at all. This proves that includeProfileSpecificSources is not
	 * relevant for labels based searches. Notice that we do read from: 'a-by-name-k8s',
	 * but not from 'a-by-label-k8s'.
	 */
	@Test
	void testAByLabelAndProfile() {
		assertThat(aByLabel.aByLabelAndProfile()).isNull();
	}

	static void setUpBeforeClass(KubernetesClient mockClient) {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "spring-k8s");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		ConfigMap aByName = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withName("a-by-name").withNamespace("spring-k8s").build())
			.addToData(Map.of("aByName", "aByName"))
			.build();
		createConfigmap(aByName);

		ConfigMap aByNameAndProfile = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withName("a-by-name-k8s").withNamespace("spring-k8s").build())
			.addToData(Map.of("aByNameK8s", "aByNameK8s"))
			.build();
		createConfigmap(aByNameAndProfile);

		ConfigMap aByLabel = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withName("a-by-label")
				.withLabels(Map.of("color", "blue"))
				.withNamespace("spring-k8s")
				.build())
			.addToData(Map.of("aByLabel", "aByLabel"))
			.build();
		createConfigmap(aByLabel);

		ConfigMap aByLabelAndProfile = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withName("a-by-label-k8s")
				.withLabels(Map.of("color", "blue"))
				.withNamespace("spring-k8s")
				.build())
			.addToData(Map.of("aByLabel", "aByLabel"))
			.build();
		createConfigmap(aByLabelAndProfile);

	}

	static void createConfigmap(ConfigMap configMap) {
		mockClient.configMaps().resource(configMap).create();
	}

}
