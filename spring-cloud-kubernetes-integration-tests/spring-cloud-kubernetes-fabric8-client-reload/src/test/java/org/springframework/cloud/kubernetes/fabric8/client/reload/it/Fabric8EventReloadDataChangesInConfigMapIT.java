/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.reload.it;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.fabric8.client.reload.RightProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.assertReloadLogStatements;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.configMap;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.replaceConfigMap;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.config.reload=debug",
		"spring.cloud.bootstrap.enabled=true" })
@ActiveProfiles("one")
class Fabric8EventReloadDataChangesInConfigMapIT extends Fabric8EventReloadBase {

	private static final String NAMESPACE = "right";

	private static ConfigMap configMap;

	@Autowired
	private RightProperties properties;

	@Autowired
	private KubernetesClient kubernetesClient;

	@BeforeAll
	static void beforeAllLocal() {
		InputStream rightConfigMapStream = util.inputStream("manifests/right-configmap.yaml");
		configMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);

		util.createNamespace(NAMESPACE);
		configMap(Phase.CREATE, util, configMap, NAMESPACE);
	}

	@AfterAll
	static void afterAllLocal() {
		configMap(Phase.DELETE, util, configMap, NAMESPACE);
		util.deleteNamespace(NAMESPACE);
	}

	/**
	 * <pre>
	 *     - configMap with no labels and data: right.value = right-initial exists in namespace right
	 *
	 *     - then we change the configmap by adding a label, this in turn does not
	 *       change the result, because the data has not changed.
	 *
	 *     - then we change data inside the config map, and we must see the updated value
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		assertReloadLogStatements("added configmap informer for namespace", "added secret informer for namespace",
				output);

		// we first read the initial value from configmap
		assertThat(properties.getValue()).isEqualTo("right-initial");

		// then deploy a new version of right-configmap, but without changing its data,
		// only add a label
		ConfigMap configMap = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("new-label", "abc"))
				.withNamespace(NAMESPACE)
				.withName("right-configmap")
				.build())
			.withData(Map.of("right.value", "right-initial"))
			.build();

		replaceConfigMap(kubernetesClient, configMap, NAMESPACE);

		await().atMost(Duration.ofSeconds(60))
			.pollDelay(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("ConfigMap right-configmap was updated in namespace right"));

		await().atMost(Duration.ofSeconds(60))
			.pollDelay(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("data in configmap has not changed, will not reload"));

		assertThat(properties.getValue()).isEqualTo("right-initial");

		// change data
		configMap = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("new-label", "abc"))
				.withNamespace(NAMESPACE)
				.withName("right-configmap")
				.build())
			.withData(Map.of("right.value", "right-after-change"))
			.build();

		replaceConfigMap(kubernetesClient, configMap, NAMESPACE);

		await().atMost(Duration.ofSeconds(60)).pollDelay(Duration.ofSeconds(1)).until(() -> {
			String afterUpdateRightValue = properties.getValue();
			return afterUpdateRightValue.equals("right-after-change");
		});
	}

}
