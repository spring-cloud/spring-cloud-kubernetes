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

package org.springframework.cloud.kubernetes.client.config;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code kubernetes:} Config Data import registers a single
 * {@link KubernetesClientProperties} bean when Commons auto-configuration is loaded in
 * the same application context. ConfigMap and Secret loading are disabled so the test
 * focuses on client-properties registration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.config.import=kubernetes:",
				"spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=false" },
		classes = KubernetesClientPropertiesConfigDataRegistrationTests.TestApplication.class)
class KubernetesClientPropertiesConfigDataRegistrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void configDataPropertiesPreventCommonsPropertiesFromBeingRegisteredAgain() {
		assertThat(applicationContext.getBeansOfType(KubernetesClientProperties.class))
			.containsOnlyKeys("configDataKubernetesClientProperties");
		assertThat(applicationContext.getBean(KubernetesClientProperties.class)).isNotNull();
	}

	@Configuration(proxyBeanMethods = false)
	@Import(KubernetesCommonsAutoConfiguration.class)
	static class TestApplication {

	}

}
