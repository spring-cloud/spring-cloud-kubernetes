/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Dawson
 * @author Kris Iyer - Add tests for #643
 */
@EnableKubernetesMockClient(crud = true, https = false)
class KubernetesConfigConfigurationTest extends KubernetesConfigTestBase {

	// injected because of @EnableKubernetesMockClient. Because of the way
	// KubernetesMockServerExtension
	// injects this field (it searches for a static KubernetesClient field in the test
	// class), we can't have a common
	// class where this configuration is present.
	private static KubernetesClient mockClient;

	@Test
	void kubernetesBootstrapWhenKubernetesDefaultEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.client.namespace=default", "spring.cloud.bootstrap.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesConfigDataWhenKubernetesDefaultEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.client.namespace=default", "spring.config.import=kubernetes:");
		assertThat(getContext().containsBean("configDataConfigMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("configDataSecretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesWhenKubernetesDisabled() {
		setup(KubernetesClientTestConfiguration.class);
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	void kubernetesWhenKubernetesConfigAndSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	void kubernetesBootstrapWhenKubernetesConfigEnabledButSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.client.namespace=default",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	void kubernetesConfigDataWhenKubernetesConfigEnabledButSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.client.namespace=default",
				"spring.main.cloud-platform=KUBERNETES", "spring.config.import=kubernetes:");
		assertThat(getContext().containsBean("configDataConfigMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("configDataSecretsPropertySourceLocator")).isFalse();
	}

	@Test
	void kubernetesBootstrapWhenKubernetesConfigDisabledButSecretEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.bootstrap.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesConfigDataWhenKubernetesConfigDisabledButSecretEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:");
		assertThat(getContext().containsBean("configDataConfigMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("configDataSecretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesBootstrapConfigWhenKubernetesEnabledAndKubernetesConfigEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true", "spring.cloud.kubernetes.client.namespace=default",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesConfigDataConfigWhenKubernetesEnabledAndKubernetesConfigEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true", "spring.cloud.kubernetes.client.namespace=default",
				"spring.main.cloud-platform=KUBERNETES", "spring.config.import=kubernetes:");
		assertThat(getContext().containsBean("configDataConfigMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("configDataSecretsPropertySourceLocator")).isTrue();
	}

	@Test
	void kubernetesConfigWhenKubernetesEnabledAndKubernetesConfigDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
	}

	@Configuration(proxyBeanMethods = false)
	private static class KubernetesClientTestConfiguration {

		@ConditionalOnMissingBean(KubernetesClient.class)
		@Bean
		KubernetesClient kubernetesClient() {
			return mockClient;
		}

	}

}
