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

package org.springframework.cloud.kubernetes.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.kubernetes.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Dawson
 * @author Kris Iyer - Add tests for #643
 */
public class KubernetesConfigConfigurationTest {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void kubernetesWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigEnabledButSecretDisabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=true", "spring.cloud.kubernetes.secrets.enabled=false");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isFalse();
	}

	@Test
	public void kubernetesWhenKubernetesConfigDisabledButSecretEnabled() throws Exception {
		setup("spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=true");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	public void kubernetesDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isTrue();
	}

	@Test
	public void kubernetesReloadEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.context.containsBean("secretsPropertyChangeEventWatcher")).isTrue();
	}

	@Test
	public void kubernetesReloadEnabledWithPolling() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.reload.mode=polling");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("configMapPropertyChangePollingWatcher")).isTrue();
		assertThat(this.context.containsBean("secretsPropertyChangePollingWatcher")).isTrue();
		assertThat(this.context.containsBean("configMapPropertyChangeEventWatcher")).isFalse();
		assertThat(this.context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.config.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isTrue();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.context.containsBean("configMapPropertyChangeEventWatcher")).isTrue();
		assertThat(this.context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	@Test
	public void kubernetesReloadEnabledButSecretAndConfigDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true", "spring.cloud.kubernetes.config.enabled=false",
				"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.reload.enabled=true");
		assertThat(this.context.containsBean("configMapPropertySourceLocator")).isFalse();
		assertThat(this.context.containsBean("secretsPropertySourceLocator")).isFalse();
		assertThat(this.context.containsBean("configMapPropertyChangeEventWatcher")).isFalse();
		assertThat(this.context.containsBean("secretsPropertyChangeEventWatcher")).isFalse();
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(PropertyPlaceholderAutoConfiguration.class,
				KubernetesClientTestConfiguration.class, BootstrapConfiguration.class,
				ConfigReloadAutoConfiguration.class, RefreshAutoConfiguration.class)
						.web(org.springframework.boot.WebApplicationType.NONE).properties(env).run();
	}

	@Configuration(proxyBeanMethods = false)
	static class KubernetesClientTestConfiguration {

		@Bean
		KubernetesClient kubernetesClient() {
			return mock(KubernetesClient.class);
		}

	}

}
