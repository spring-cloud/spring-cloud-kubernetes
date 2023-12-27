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

package org.springframework.cloud.kubernetes.commons;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesCommonsSanitizeAutoConfigurationTests {

	@Test
	void sanitizeSecretsNotEnabled() {
		contextRunner(false, false)
				.run(context -> assertThat(context.getBeansOfType(SanitizingFunction.class)).isEmpty());
	}

	@Test
	void sanitizeSecretsEnabled() {
		contextRunner(true, false)
				.run(context -> assertThat(context.getBeansOfType(SanitizingFunction.class)).hasSize(1));
	}

	@Test
	void sanitizeSecretsEnabledSanitizedClassNotPresent() {
		contextRunner(true, true)
				.run(context -> assertThat(context.getBeansOfType(SanitizingFunction.class)).isEmpty());
	}

	private ApplicationContextRunner contextRunner(boolean enableSanitizeSecrets, boolean filterSanitizedDataClass) {

		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesCommonsSanitizeAutoConfiguration.class));

		if (enableSanitizeSecrets) {
			contextRunner = contextRunner.withPropertyValues(ConditionalOnSanitizeSecrets.VALUE + "=true");
		}

		if (filterSanitizedDataClass) {
			contextRunner = contextRunner.withClassLoader(new FilteredClassLoader(SanitizableData.class));
		}

		contextRunner = contextRunner.withPropertyValues("spring.main.cloud-platform=KUBERNETES");

		return contextRunner;

	}

}
