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

package org.springframework.cloud.kubernetes.commons.config.bootstrap;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.config.App;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = App.class,
		properties = { "spring.cloud.kubernetes.secrets.fail-fast=true",
				"spring.cloud.kubernetes.secrets.retry.max-attempts=3",
				"spring.cloud.kubernetes.secrets.retry.initial-interval=1500",
				"spring.cloud.kubernetes.secrets.retry.max-interval=3000",
				"spring.cloud.kubernetes.secrets.retry.multiplier=1.5", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.config.enabled=false" })
class SecretsFailFastEnabledWithCustomRetryConfiguration {

	@Autowired
	private SecretsConfigProperties secretsConfigProperties;

	@Test
	void retryConfigurationShouldBeCustomized() {
		RetryProperties retryProperties = secretsConfigProperties.retry();

		assertThat(retryProperties.maxAttempts()).isEqualTo(3);
		assertThat(retryProperties.initialInterval()).isEqualTo(1500L);
		assertThat(retryProperties.maxInterval()).isEqualTo(3000L);
		assertThat(retryProperties.multiplier()).isEqualTo(1.5D);
	}

}
