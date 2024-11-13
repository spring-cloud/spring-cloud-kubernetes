/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.reload_it;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.main.cloud-platform=kubernetes", "spring.cloud.kubernetes.reload.enabled=true",
	"spring.main.allow-bean-definition-overriding=true" },
	classes = { ReloadConfigMapTest.TestConfig.class })
class ReloadConfigMapTest {

	private static boolean [] strategyCalled = new boolean[] { false };

	@Test
	void test() {
		assertThat("1").isEqualTo("1");
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		PollingConfigMapChangeDetector pollingConfigMapChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy) {
			return new PollingConfigMapChangeDetector(environment, configReloadProperties, configurationUpdateStrategy,
				Fabric8ConfigMapPropertySource.class, null, null);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {
			return new MockEnvironment();
		}

		@Bean
		@Primary
		ConfigReloadProperties configReloadProperties() {
			return new ConfigReloadProperties(true, true, false,
				ConfigReloadProperties.ReloadStrategy.REFRESH, ConfigReloadProperties.ReloadDetectionMode.POLLING,
				Duration.ofMillis(15000), Set.of("non-default"), false, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> {

			});
		}

		@Bean
		@Primary
		Fabric8ConfigMapPropertySourceLocator fabric8ConfigMapPropertySourceLocator() {
			return new Fabric8ConfigMapPropertySourceLocator();
		}

	}

}
