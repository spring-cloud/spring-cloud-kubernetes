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

import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Isik Erhan
 */
class FailFastEnabledWithoutSpringRetryOnClasspath {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(KubernetesBootstrapConfiguration.class))
			.withClassLoader(new FilteredClassLoader(Retryable.class, Aspect.class, AopAutoConfiguration.class));

	@Test
	void shouldNotDefineRetryBeansWhenConfigMapFailFastEnabled() {
		contextRunner.withPropertyValues("spring.cloud.kubernetes.config.fail-fast=true")
				.run(context -> assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty());
	}

	@Test
	void shouldNotDefineRetryBeansWhenSecretsFailFastEnabled() {
		contextRunner.withPropertyValues("spring.cloud.kubernetes.secrets.fail-fast=true")
				.run(context -> assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty());
	}

	@Test
	void shouldNotDefineRetryBeansWhenConfigMapAndSecretsFailFastEnabled() {
		contextRunner
				.withPropertyValues("spring.cloud.kubernetes.config.fail-fast=true",
						"spring.cloud.kubernetes.secrets.fail-fast=true")
				.run(context -> assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty());
	}

}
