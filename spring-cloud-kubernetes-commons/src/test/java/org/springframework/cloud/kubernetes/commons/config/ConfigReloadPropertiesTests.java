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

package org.springframework.cloud.kubernetes.commons.config;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
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
			Assertions.assertNotNull(properties);
			Assertions.assertFalse(properties.enabled());
			Assertions.assertTrue(properties.monitoringConfigMaps());
			Assertions.assertFalse(properties.monitoringSecrets());
			Assertions.assertEquals(ConfigReloadProperties.ReloadStrategy.REFRESH, properties.strategy());
			Assertions.assertEquals(ConfigReloadProperties.ReloadDetectionMode.EVENT, properties.mode());
			Assertions.assertEquals(Duration.ofMillis(15000), properties.period());
			Assertions.assertTrue(properties.namespaces().isEmpty());
			Assertions.assertEquals(Duration.ofSeconds(2), properties.maxWaitForRestart());
		});
	}

	@Test
	void testNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.cloud.kubernetes.reload.enabled=true",
						"spring.cloud.kubernetes.reload.monitoring-config-maps=false",
						"spring.cloud.kubernetes.reload.monitoring-secrets=true",
						"spring.cloud.kubernetes.reload.strategy=SHUTDOWN",
						"spring.cloud.kubernetes.reload.mode=POLLING", "spring.cloud.kubernetes.reload.period=1000ms",
						"spring.cloud.kubernetes.reload.namespaces[0]=a",
						"spring.cloud.kubernetes.reload.namespaces[1]=b",
						"spring.cloud.kubernetes.reload.max-wait-for-restart=5s")
				.run(context -> {
					ConfigReloadProperties properties = context.getBean(ConfigReloadProperties.class);
					Assertions.assertNotNull(properties);
					Assertions.assertTrue(properties.enabled());
					Assertions.assertFalse(properties.monitoringConfigMaps());
					Assertions.assertTrue(properties.monitoringSecrets());
					Assertions.assertEquals(ConfigReloadProperties.ReloadStrategy.SHUTDOWN, properties.strategy());
					Assertions.assertEquals(ConfigReloadProperties.ReloadDetectionMode.POLLING, properties.mode());
					Assertions.assertEquals(Duration.ofMillis(1000), properties.period());
					Assertions.assertEquals(Set.of("a", "b"), properties.namespaces());
					Assertions.assertEquals(Duration.ofSeconds(5), properties.maxWaitForRestart());
				});
	}

	@Configuration
	@EnableConfigurationProperties(ConfigReloadProperties.class)
	static class Config {

	}

}
