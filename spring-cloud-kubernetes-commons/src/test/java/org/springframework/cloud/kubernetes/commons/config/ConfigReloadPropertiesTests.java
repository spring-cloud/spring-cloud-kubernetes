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

package org.springframework.cloud.kubernetes.commons.config;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author wind57
 *
 * Tests binding, since we moved from a class to a record
 */
class ConfigReloadPropertiesTests {

	@Test
	void testDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			ConfigReloadProperties properties = context.getBean(ConfigReloadProperties.class);
			Assertions.assertThat(properties).isNotNull();
			Assertions.assertThat(properties.enabled()).isFalse();
			Assertions.assertThat(properties.monitoringConfigMaps()).isTrue();
			Assertions.assertThat(properties.monitoringSecrets()).isFalse();
			Assertions.assertThat(ConfigReloadProperties.ReloadStrategy.REFRESH).isEqualTo(properties.strategy());
			Assertions.assertThat(ConfigReloadProperties.ReloadDetectionMode.EVENT).isEqualTo(properties.mode());
			Assertions.assertThat(Duration.ofMillis(15000)).isEqualTo(properties.period());
			Assertions.assertThat(properties.namespaces().isEmpty()).isTrue();
			Assertions.assertThat(Duration.ofSeconds(2)).isEqualTo(properties.maxWaitForRestart());
		});
	}

	@Test
	void testNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
			.withPropertyValues("spring.cloud.kubernetes.reload.enabled=true",
					"spring.cloud.kubernetes.reload.monitoring-config-maps=false",
					"spring.cloud.kubernetes.reload.monitoring-secrets=true",
					"spring.cloud.kubernetes.reload.strategy=SHUTDOWN", "spring.cloud.kubernetes.reload.mode=POLLING",
					"spring.cloud.kubernetes.reload.period=1000ms", "spring.cloud.kubernetes.reload.namespaces[0]=a",
					"spring.cloud.kubernetes.reload.namespaces[1]=b",
					"spring.cloud.kubernetes.reload.max-wait-for-restart=5s")
			.run(context -> {
				ConfigReloadProperties properties = context.getBean(ConfigReloadProperties.class);
				Assertions.assertThat(properties).isNotNull();
				Assertions.assertThat(properties.enabled()).isTrue();
				Assertions.assertThat(properties.monitoringConfigMaps()).isFalse();
				Assertions.assertThat(properties.monitoringSecrets()).isTrue();
				Assertions.assertThat(ConfigReloadProperties.ReloadStrategy.SHUTDOWN).isEqualTo(properties.strategy());
				Assertions.assertThat(ConfigReloadProperties.ReloadDetectionMode.POLLING).isEqualTo(properties.mode());
				Assertions.assertThat(Duration.ofMillis(1000)).isEqualTo(properties.period());
				Assertions.assertThat(properties.namespaces()).containsExactlyInAnyOrder("a", "b");
				Assertions.assertThat(Duration.ofSeconds(5)).isEqualTo(properties.maxWaitForRestart());
			});
	}

	@Configuration
	@EnableConfigurationProperties(ConfigReloadProperties.class)
	static class Config {

	}

}
