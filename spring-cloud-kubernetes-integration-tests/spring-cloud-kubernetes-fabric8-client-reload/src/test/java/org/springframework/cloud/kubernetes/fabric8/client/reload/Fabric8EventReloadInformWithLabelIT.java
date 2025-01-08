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

package org.springframework.cloud.kubernetes.fabric8.client.reload;

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
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.TestAssertions.assertReloadLogStatements;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.TestAssertions.configMap;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.TestAssertions.replaceConfigMap;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.config.reload=debug",
		"spring.cloud.bootstrap.enabled=true" })
@ActiveProfiles("three")
class Fabric8EventReloadInformWithLabelIT extends Fabric8EventReloadBase {

	private static final String RIGHT_NAMESPACE = "right";

	private static ConfigMap rightConfigMap;

	private static ConfigMap rightConfigMapWithLabel;

	@Autowired
	private KubernetesClient kubernetesClient;

	@Autowired
	private RightProperties rightProperties;

	@Autowired
	private RightWithLabelsProperties rightWithLabelsProperties;

	@BeforeAll
	static void beforeAllLocal() {
		InputStream rightConfigMapStream = util.inputStream("manifests/right-configmap.yaml");
		InputStream rightConfigMapWithLabelStream = util.inputStream("manifests/right-configmap-with-label.yaml");

		rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);
		rightConfigMapWithLabel = Serialization.unmarshal(rightConfigMapWithLabelStream, ConfigMap.class);

		util.createNamespace(RIGHT_NAMESPACE);

		configMap(Phase.CREATE, util, rightConfigMap, RIGHT_NAMESPACE);
		configMap(Phase.CREATE, util, rightConfigMapWithLabel, RIGHT_NAMESPACE);
	}

	@AfterAll
	static void afterAllLocal() {
		configMap(Phase.DELETE, util, rightConfigMap, RIGHT_NAMESPACE);
		configMap(Phase.DELETE, util, rightConfigMapWithLabel, RIGHT_NAMESPACE);
		util.deleteNamespace(RIGHT_NAMESPACE);
	}

	/**
	 * <pre>
	 * - there is one namespace : right
	 * - right has two configmaps: right-configmap, right-configmap-with-label
	 * - we watch the "right" namespace, but enable tagging; which means that only
	 * right-configmap-with-label triggers changes.
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		assertReloadLogStatements("added configmap informer for namespace", "added secret informer for namespace",
				output);

		// read the initial value from the right-configmap
		assertThat(rightProperties.getValue()).isEqualTo("right-initial");

		// then read the initial value from the right-with-label-configmap
		assertThat(rightWithLabelsProperties.getValue()).isEqualTo("right-with-label-initial");

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
			.withData(Map.of("right.value", "right-after-change"))
			.build();

		replaceConfigMap(kubernetesClient, rightConfigMapAfterChange, RIGHT_NAMESPACE);

		// nothing changes in our app, because we are watching only labeled configmaps
		assertThat(rightProperties.getValue()).isEqualTo("right-initial");
		assertThat(rightWithLabelsProperties.getValue()).isEqualTo("right-with-label-initial");

		// then deploy a new version of right-with-label-configmap
		ConfigMap rightWithLabelConfigMapAfterChange = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap-with-label").build())
			.withData(Map.of("right.with.label.value", "right-with-label-after-change"))
			.build();

		replaceConfigMap(kubernetesClient, rightWithLabelConfigMapAfterChange, RIGHT_NAMESPACE);

		// since we have changed a labeled configmap, app will restart and pick up the new
		// value
		await().atMost(Duration.ofSeconds(60)).pollDelay(Duration.ofSeconds(1)).until(() -> {
			String afterUpdateRightValue = rightWithLabelsProperties.getValue();
			return afterUpdateRightValue.equals("right-with-label-after-change");
		});

		// right-configmap now will see the new value also, but only because the other
		// configmap has triggered the restart
		await().atMost(Duration.ofSeconds(60)).pollDelay(Duration.ofSeconds(1)).until(() -> {
			String afterUpdateRightValue = rightProperties.getValue();
			return afterUpdateRightValue.equals("right-after-change");
		});
	}

}
