/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.reload;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.commons.util.TaskSchedulerWrapper;
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class ConfigReloadAutoConfigurationApplicationContextTests {

	private ApplicationContextRunner applicationContextRunner;

	/**
	 * no special properties provided.
	 */
	@Test
	void testDefault() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * reload is disabled.
	 */
	@Test
	void testReloadDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).doesNotHaveBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).doesNotHaveBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * config maps support is enabled.
	 */
	@Test
	void testConfigMapsSupportEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * secrets support is enabled.
	 */
	@Test
	void testSecretsSupportEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.secrets.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * config maps and secrets support is enabled.
	 */
	@Test
	void testConfigMapsAndSecretsSupportEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true", "spring.cloud.kubernetes.secrets.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * config maps and secrets support is disabled.
	 */
	@Test
	void testConfigMapsAndSecretsSupportDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).doesNotHaveBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).doesNotHaveBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * config maps support disabled and secrets support is enabled.
	 */
	@Test
	void testConfigMapsDisabledAndSecretsSupportEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.secrets.enabled=true");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(ConfigurationUpdateStrategy.class));
	}

	/**
	 * config maps support enabled and secrets support is disable.
	 */
	@Test
	void testConfigMapsEnabledAndSecretsSupportDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.reload.enabled=true",
				"spring.cloud.kubernetes.config.enabled=true", "spring.cloud.kubernetes.secrets.enabled=false");
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(TaskSchedulerWrapper.class));
		applicationContextRunner.run(context -> assertThat(context).hasSingleBean(ConfigurationUpdateStrategy.class));
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(InfoEndpointAutoConfiguration.class,
						RefreshEndpointAutoConfiguration.class, ConfigReloadPropertiesAutoConfiguration.class,
						RefreshAutoConfiguration.class, ConfigReloadAutoConfiguration.class))
				.withUserConfiguration(RebinderConfig.class).withPropertyValues(properties);
	}

	@TestConfiguration
	static class RebinderConfig {

		@Bean
		ConfigurationPropertiesRebinder rebinder() {
			return Mockito.mock(ConfigurationPropertiesRebinder.class);
		}

	}

}
