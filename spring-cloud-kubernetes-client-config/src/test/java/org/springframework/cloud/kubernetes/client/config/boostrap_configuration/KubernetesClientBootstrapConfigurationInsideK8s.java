/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.boostrap_configuration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.config.Application;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=abc",
				"spring.cloud.bootstrap.enabled=true" })
class KubernetesClientBootstrapConfigurationInsideK8s {

	@Autowired
	private ConfigurableApplicationContext context;

	// tests that @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES) has the desired
	// effect, meaning when it is enabled, both property sources are present
	@Test
	public void bothPresent() {
		assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(1);
		assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(1);
	}

}
