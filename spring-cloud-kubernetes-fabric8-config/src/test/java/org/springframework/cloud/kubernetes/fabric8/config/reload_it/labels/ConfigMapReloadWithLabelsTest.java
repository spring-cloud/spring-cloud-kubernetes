/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.reload_it.labels;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.allow-bean-definition-overriding=true", "configmaps.labels.filtering=true" },
		classes = { CommonAbstractFiltering.TestConfig.class,
				CommonAbstractFiltering.ConfigReloadPropertiesConfiguration.class })
@ContextConfiguration(initializers = CommonAbstractFiltering.Initializer.class)
class ConfigMapReloadWithLabelsTest extends CommonAbstractFiltering {

	/**
	 * <pre>
	 *     - we only watch configmaps with labels: { only-shape:round }
	 *     - configmap that we created has such labels
	 * </pre>
	 */
	@Test
	void test() {
		ConfigMap configMap = configMap(CONFIG_MAP_NAME, Map.of("a", "b"), Map.of("only-shape", "round"));

		kubernetesClient.configMaps().inNamespace(NAMESPACE).resource(configMap).create();
		awaitUntil(10, 1000, reloadProbe::isCalled);
	}

}
