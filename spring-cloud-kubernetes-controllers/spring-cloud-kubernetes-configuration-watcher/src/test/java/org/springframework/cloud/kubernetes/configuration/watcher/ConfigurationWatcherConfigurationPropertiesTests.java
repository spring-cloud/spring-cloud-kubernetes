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

package org.springframework.cloud.kubernetes.configuration.watcher;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
class ConfigurationWatcherConfigurationPropertiesTests {

	@Test
	void setActuatorPath() {
		ConfigurationWatcherConfigurationProperties properties = new ConfigurationWatcherConfigurationProperties();
		properties.setActuatorPath("foo");
		assertThat(properties.getActuatorPath()).isEqualTo("/foo");
		properties.setActuatorPath("/foo/bar/");
		assertThat(properties.getActuatorPath()).isEqualTo("/foo/bar");
	}

	@Test
	void testWithDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			ConfigurationWatcherConfigurationProperties props = context
				.getBean(ConfigurationWatcherConfigurationProperties.class);
			assertThat(props).isNotNull();
			assertThat(props.getHa().isEnabled()).isFalse();
			assertThat(props.getHa().getLeaseName()).isEqualTo("configuration-watcher-ha");
			assertThat(props.getHa().getLeaseNamespace()).isEqualTo("default");
		});
	}

	@Test
	void testWithNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
			.withPropertyValues("spring.cloud.kubernetes.configuration.watcher.ha.enabled=true",
					"spring.cloud.kubernetes.configuration.watcher.ha.lease-name=custom-lease",
					"spring.cloud.kubernetes.configuration.watcher.ha.lease-namespace=watcher-namespace")
			.run(context -> {
				ConfigurationWatcherConfigurationProperties props = context
					.getBean(ConfigurationWatcherConfigurationProperties.class);
				assertThat(props).isNotNull();
				assertThat(props.getHa().isEnabled()).isTrue();
				assertThat(props.getHa().getLeaseName()).isEqualTo("custom-lease");
				assertThat(props.getHa().getLeaseNamespace()).isEqualTo("watcher-namespace");
			});
	}

	@Configuration
	@EnableConfigurationProperties(ConfigurationWatcherConfigurationProperties.class)
	static class Config {

	}

}
