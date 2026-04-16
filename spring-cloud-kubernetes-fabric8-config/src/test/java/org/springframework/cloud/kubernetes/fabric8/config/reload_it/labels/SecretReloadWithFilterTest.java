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

import io.fabric8.kubernetes.api.model.Secret;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities.awaitUntil;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.main.allow-bean-definition-overriding=true", "secrets.reload.filtering=true" },
		classes = { CommonAbstractFiltering.TestConfig.class,
				CommonAbstractFiltering.ConfigReloadPropertiesConfiguration.class })
@ContextConfiguration(initializers = CommonAbstractFiltering.Initializer.class)
class SecretReloadWithFilterTest extends CommonAbstractFiltering {

	/**
	 * <pre>
	 *     - we enable reload filtering, via 'spring.cloud.kubernetes.reload.enable-reload-filtering=true'
	 *       ( this is done in ConfigReloadProperties )
	 *     - as such, only secrets that have 'spring.cloud.kubernetes.config.informer.enabled=true'
	 *       label are being watched. This is what the informer is created with.
	 * </pre>
	 */
	@Test
	void test() {
		Secret secret = secret(SECRET_NAME, Map.of("a", "b"),
				Map.of("spring.cloud.kubernetes.config.informer.enabled", "true"));

		kubernetesClient.secrets().inNamespace(NAMESPACE).resource(secret).create();
		awaitUntil(10, 1000, reloadProbe::isCalled);
	}

}
