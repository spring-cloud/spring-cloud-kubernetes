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
@ActiveProfiles("two")
class Fabric8EventReloadInformIT extends Fabric8EventReloadBase {

	private static final String LEFT_NAMESPACE = "left";

	private static final String RIGHT_NAMESPACE = "right";

	private static ConfigMap leftConfigMap;

	private static ConfigMap rightConfigMap;

	@Autowired
	private LeftProperties leftProperties;

	@Autowired
	private RightProperties rightProperties;

	@Autowired
	private KubernetesClient kubernetesClient;

	@BeforeAll
	static void beforeAllLocal() {
		InputStream leftConfigMapStream = util.inputStream("manifests/left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("manifests/right-configmap.yaml");

		leftConfigMap = Serialization.unmarshal(leftConfigMapStream, ConfigMap.class);
		rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);

		util.createNamespace(LEFT_NAMESPACE);
		util.createNamespace(RIGHT_NAMESPACE);

		configMap(Phase.CREATE, util, leftConfigMap, LEFT_NAMESPACE);
		configMap(Phase.CREATE, util, rightConfigMap, RIGHT_NAMESPACE);
	}

	@AfterAll
	static void afterAllLocal() {
		configMap(Phase.DELETE, util, leftConfigMap, LEFT_NAMESPACE);
		configMap(Phase.DELETE, util, rightConfigMap, RIGHT_NAMESPACE);

		util.deleteNamespace(LEFT_NAMESPACE);
		util.deleteNamespace(RIGHT_NAMESPACE);
	}

	/**
	 * <pre>
	 * - there are two namespaces : left and right
	 * - each of the namespaces has one configmap
	 * - we watch the "right" namespace and make a change in the configmap in the same
	 * namespace
	 * - as such, event is triggered (refresh happens) and we see the updated value
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		assertReloadLogStatements("added configmap informer for namespace", "added secret informer for namespace",
				output);

		// first we read these with default values
		assertThat(leftProperties.getValue()).isEqualTo("left-initial");
		assertThat(rightProperties.getValue()).isEqualTo("right-initial");

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withNamespace(RIGHT_NAMESPACE).withName("right-configmap").build())
			.withData(Map.of("right.value", "right-after-change"))
			.build();

		replaceConfigMap(kubernetesClient, rightConfigMapAfterChange, RIGHT_NAMESPACE);

		await().atMost(Duration.ofSeconds(60)).pollDelay(Duration.ofSeconds(1)).until(() -> {
			String afterUpdateRightValue = rightProperties.getValue();
			return afterUpdateRightValue.equals("right-after-change");
		});

		// left does not change
		assertThat(leftProperties.getValue()).isEqualTo("left-initial");
	}

}
