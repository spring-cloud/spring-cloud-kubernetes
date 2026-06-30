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
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class ConfigReloadPropertiesTests {

	@Test
	void testDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			ConfigReloadProperties properties = context.getBean(ConfigReloadProperties.class);
			assertThat(properties).isNotNull();
			assertThat(properties.enabled()).isFalse();
			assertThat(properties.monitoringConfigMaps()).isTrue();
			assertThat(properties.configMapsLabels()).isEmpty();
			assertThat(properties.monitoringSecrets()).isFalse();
			assertThat(properties.secretsLabels()).isEmpty();
			assertThat(ConfigReloadProperties.ReloadStrategy.REFRESH).isEqualTo(properties.strategy());
			assertThat(ConfigReloadProperties.ReloadDetectionMode.EVENT).isEqualTo(properties.mode());
			assertThat(Duration.ofMillis(15000)).isEqualTo(properties.period());
			assertThat(properties.namespaces().isEmpty()).isTrue();
			assertThat(Duration.ofSeconds(2)).isEqualTo(properties.maxWaitForRestart());
		});
	}

	@Test
	void testNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
			.withPropertyValues("spring.cloud.kubernetes.reload.enabled=true",
					"spring.cloud.kubernetes.reload.monitoring-config-maps=false",
					"spring.cloud.kubernetes.reload.config-maps-labels[aa]=bb",
					"spring.cloud.kubernetes.reload.monitoring-secrets=true",
					"spring.cloud.kubernetes.reload.secrets-labels[cc]=dd",
					"spring.cloud.kubernetes.reload.strategy=SHUTDOWN", "spring.cloud.kubernetes.reload.mode=POLLING",
					"spring.cloud.kubernetes.reload.period=1000ms", "spring.cloud.kubernetes.reload.namespaces[0]=a",
					"spring.cloud.kubernetes.reload.namespaces[1]=b",
					"spring.cloud.kubernetes.reload.max-wait-for-restart=5s")
			.run(context -> {
				ConfigReloadProperties properties = context.getBean(ConfigReloadProperties.class);
				assertThat(properties).isNotNull();
				assertThat(properties.enabled()).isTrue();
				assertThat(properties.monitoringConfigMaps()).isFalse();
				assertThat(properties.configMapsLabels()).containsExactlyInAnyOrderEntriesOf(Map.of("aa", "bb"));
				assertThat(properties.monitoringSecrets()).isTrue();
				assertThat(properties.secretsLabels()).containsExactlyInAnyOrderEntriesOf(Map.of("cc", "dd"));
				assertThat(ConfigReloadProperties.ReloadStrategy.SHUTDOWN).isEqualTo(properties.strategy());
				assertThat(ConfigReloadProperties.ReloadDetectionMode.POLLING).isEqualTo(properties.mode());
				assertThat(Duration.ofMillis(1000)).isEqualTo(properties.period());
				assertThat(properties.namespaces()).containsExactlyInAnyOrder("a", "b");
				assertThat(Duration.ofSeconds(5)).isEqualTo(properties.maxWaitForRestart());
			});
	}

	@Configuration
	@EnableConfigurationProperties(ConfigReloadProperties.class)
	static class Config {

	}

}
