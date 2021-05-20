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

package org.springframework.cloud.kubernetes.fabric8.config;

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
public class KubernetesConfigConfigurationTest extends KubernetesConfigTestBase {

	// injected because of @EnableKubernetesMockClient. Because of the way
	// KubernetesMockServerExtension
	// injects this field (it searches for a static KubernetesClient field in the test
	// class), we can't have a common
	// class where this configuration is present.
	private static KubernetesClient mockClient;

	@Test
	public void kubernetesWhenKubernetesDefaultEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	public void kubernetesWhenKubernetesDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=false");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigAndSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigEnabledButSecretDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigDisabledButSecretEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	public void kubernetesConfigWhenKubernetesEnabledAndKubernetesConfigEnabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true");
		assertThat(getContext().containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(getContext().containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	public void kubernetesConfigWhenKubernetesEnabledAndKubernetesConfigDisabled() {
		setup(KubernetesClientTestConfiguration.class, "spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.config.enabled=false");
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
