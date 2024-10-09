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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
	"logging.level.org.springframework.cloud.kubernetes.fabric8.config.reload=debug",
	"spring.cloud.bootstrap.enabled=true", "spring.cloud.kubernetes.client.namespace=default" })
class Fabric8EventReloadInformFromOneNamespaceEventNotTriggeredIT extends Fabric8EventReloadBase {

	@BeforeEach
	void beforeEach() {
		util.createNamespace("left");
		util.createNamespace("right");

		leftAndRightConfigMap(Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		leftAndRightConfigMap(Phase.DELETE);

		util.deleteNamespace("left");
		util.deleteNamespace("right");
	}

	@Test
	void test() {

	}

	private void leftAndRightConfigMap(Phase phase) {
		InputStream leftConfigMapStream = util.inputStream("left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("right-configmap.yaml");
		ConfigMap leftConfigMap = Serialization.unmarshal(leftConfigMapStream, ConfigMap.class);
		ConfigMap rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait("left", leftConfigMap, null);
			util.createAndWait("right", rightConfigMap, null);
		}
		else {
			util.deleteAndWait("left", leftConfigMap, null);
			util.deleteAndWait("right", rightConfigMap, null);
		}
	}

}
