/*
 * Copyright 2013-2020 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
public class KubernetesClientBootstrapConfigurationTests {

	@SpringBootApplication
	static class Application {

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.enabled=false", "kubernetes.informer.enabled=false",
					"kubernetes.manifests.enabled=false" })
	@Nested
	class KubernetesDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void configAndSecretsBeansAreNotPresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.secrets.enabled=true",
					"spring.cloud.kubernetes.client.namespace=default", "spring.main.cloud-platform=KUBERNETES" })
	@Nested
	class KubernetesEnabledOnPurpose {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void configAndSecretsBeansArePresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.client.namespace=default",
					"spring.main.cloud-platform=KUBERNETES" })
	@Nested
	class KubernetesEnabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void configAndSecretsBeansArePresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.config.enabled=false", "spring.main.cloud-platform=KUBERNETES" })
	@Nested
	class KubernetesEnabledConfigDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void secretsOnlyPresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.secrets.enabled=false",
					"spring.cloud.kubernetes.client.namespace=default", "spring.main.cloud-platform=KUBERNETES" })
	@Nested
	class KubernetesEnabledSecretsDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void secretsOnlyPresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.secrets.enabled=false",
					"spring.cloud.kubernetes.config.enabled=false", "kubernetes.informer.enabled=false" })
	@Nested
	class KubernetesEnabledSecretsAndConfigDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void secretsOnlyPresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

	// tests that @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES) has the desired
	// effect, meaning when it is enabled, both property sources are present
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=abc" })
	@Nested
	class KubernetesClientBootstrapConfigurationInsideK8s {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void bothPresent() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(1);
		}

	}

	// tests that @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES) has the desired
	// effect, meaning when it is disabled, no property source bean is present
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
	properties = {"kubernetes.informer.enabled=false"})
	@Nested
	class KubernetesClientBootstrapConfigurationNotInsideK8s {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void bothMissing() {
			assertThat(context.getBeanNamesForType(KubernetesClientConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(KubernetesClientSecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

}
