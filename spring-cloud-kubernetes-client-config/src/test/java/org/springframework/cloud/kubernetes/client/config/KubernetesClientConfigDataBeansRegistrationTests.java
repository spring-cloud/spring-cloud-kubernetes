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

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Config Data's {@link ApiClient} and {@link CoreV1Api} are the only beans
 * of those types when Kubernetes client auto-configuration is also loaded. This guards
 * against duplicate client beans from the two paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.config.import=kubernetes:",
				"spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=false" },
		classes = KubernetesClientConfigDataBeansRegistrationTests.TestApplication.class)
class KubernetesClientConfigDataBeansRegistrationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void configDataClientBeansPreventAutoConfigurationFromRegisteringDuplicates() {
		assertThat(applicationContext.getBeansOfType(ApiClient.class)).containsOnlyKeys("configDataApiClient");
		assertThat(applicationContext.getBeansOfType(CoreV1Api.class)).containsOnlyKeys("configDataCoreV1Api");
	}

	@Configuration(proxyBeanMethods = false)
	@Import({ KubernetesCommonsAutoConfiguration.class, KubernetesClientAutoConfiguration.class })
	static class TestApplication {

	}

}
