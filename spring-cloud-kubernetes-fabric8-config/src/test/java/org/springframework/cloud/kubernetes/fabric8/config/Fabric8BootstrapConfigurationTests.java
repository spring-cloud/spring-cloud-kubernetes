/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
public class Fabric8BootstrapConfigurationTests {

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class)
	@Nested
	class KubernetesDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void configAndSecretsBeansAreNotPresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(0);
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
		public void configAndSecretsBeansArePresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(1);
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
		public void configAndSecretsBeansArePresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.config.enabled=false", "spring.main.cloud-platform=KUBERNETES" })
	@Nested
	class KubernetesEnabledConfigDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void secretsOnlyPresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(1);
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
		public void secretsOnlyPresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.cloud.kubernetes.secrets.enabled=false",
					"spring.cloud.kubernetes.config.enabled=false" })
	@Nested
	class KubernetesEnabledSecretsAndConfigDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void secretsOnlyPresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

	// tests that @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES) has the desired
	// effect, meaning when it is enabled, both property sources are present
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class,
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=abc" })
	@Nested
	class Fabric8BootstrapConfigurationInsideK8s {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void bothPresent() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(1);
		}

	}

	// tests that @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES) has the desired
	// effect, meaning when it is disabled, no property source bean is present
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Application.class)
	@Nested
	class Fabric8BootstrapConfigurationNotInsideK8s {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void bothMissing() {
			assertThat(context.getBeanNamesForType(Fabric8ConfigMapPropertySourceLocator.class)).hasSize(0);
			assertThat(context.getBeanNamesForType(Fabric8SecretsPropertySourceLocator.class)).hasSize(0);
		}

	}

}
