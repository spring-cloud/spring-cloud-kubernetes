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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.Map;

import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.config.AbstractConfigProperties.RetryProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Isik Erhan
 */
public class KubernetesBootstrapConfigurationTests {

	@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class)
	@Nested
	public class FailFastDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void shouldNotDefineRetryBeans() {
			assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty();
		}

	}

	@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class,
			properties = { "spring.cloud.kubernetes.config.fail-fast=true" })
	@Nested
	public class ConfigMapFailFastEnabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Autowired
		ConfigMapConfigProperties configMapConfigProperties;

		@Test
		public void shouldDefineRequiredBeans() {
			Map<String, RetryOperationsInterceptor> retryInterceptors = context
					.getBeansOfType(RetryOperationsInterceptor.class);
			assertThat(retryInterceptors.containsKey("configMapPropertiesRetryInterceptor")).isTrue();
			assertThat(retryInterceptors.containsKey("secretsPropertiesRetryInterceptor")).isTrue();
		}

		@Test
		public void retryConfigurationShouldBeDefault() {
			RetryProperties retryProperties = configMapConfigProperties.getRetry();
			RetryProperties defaultRetryProperties = new RetryProperties();

			assertThat(retryProperties.getMaxAttempts()).isEqualTo(defaultRetryProperties.getMaxAttempts());
			assertThat(retryProperties.getInitialInterval()).isEqualTo(defaultRetryProperties.getInitialInterval());
			assertThat(retryProperties.getMaxInterval()).isEqualTo(defaultRetryProperties.getMaxInterval());
			assertThat(retryProperties.getMultiplier()).isEqualTo(defaultRetryProperties.getMultiplier());
		}

	}

	@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class,
			properties = { "spring.cloud.kubernetes.secrets.fail-fast=true" })
	@Nested
	public class SecretsFailFastEnabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Autowired
		SecretsConfigProperties secretsConfigProperties;

		@Test
		public void shouldDefineRequiredBeans() {
			Map<String, RetryOperationsInterceptor> retryInterceptors = context
					.getBeansOfType(RetryOperationsInterceptor.class);
			assertThat(retryInterceptors.containsKey("configMapPropertiesRetryInterceptor")).isTrue();
			assertThat(retryInterceptors.containsKey("secretsPropertiesRetryInterceptor")).isTrue();
		}

		@Test
		public void retryConfigurationShouldBeDefault() {
			RetryProperties retryProperties = secretsConfigProperties.getRetry();
			RetryProperties defaultRetryProperties = new RetryProperties();

			assertThat(retryProperties.getMaxAttempts()).isEqualTo(defaultRetryProperties.getMaxAttempts());
			assertThat(retryProperties.getInitialInterval()).isEqualTo(defaultRetryProperties.getInitialInterval());
			assertThat(retryProperties.getMaxInterval()).isEqualTo(defaultRetryProperties.getMaxInterval());
			assertThat(retryProperties.getMultiplier()).isEqualTo(defaultRetryProperties.getMultiplier());
		}

	}

	@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class, properties = {
			"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.secrets.fail-fast=true" })
	@Nested
	public class ConfigMapAndSecretsFailFastEnabledWithDefaultRetryConfiguration {

		@Autowired
		ConfigurableApplicationContext context;

		@Autowired
		ConfigMapConfigProperties configMapConfigProperties;

		@Autowired
		SecretsConfigProperties secretsConfigProperties;

		@Test
		public void shouldDefineRequiredBeans() {
			Map<String, RetryOperationsInterceptor> retryInterceptors = context
					.getBeansOfType(RetryOperationsInterceptor.class);
			assertThat(retryInterceptors.containsKey("configMapPropertiesRetryInterceptor")).isTrue();
			assertThat(retryInterceptors.containsKey("secretsPropertiesRetryInterceptor")).isTrue();
		}

		@Test
		public void retryConfigurationShouldBeDefault() {
			RetryProperties defaultRetryProperties = new RetryProperties();

			RetryProperties configMapRetryProperties = configMapConfigProperties.getRetry();

			assertThat(configMapRetryProperties.getMaxAttempts()).isEqualTo(defaultRetryProperties.getMaxAttempts());
			assertThat(configMapRetryProperties.getInitialInterval())
					.isEqualTo(defaultRetryProperties.getInitialInterval());
			assertThat(configMapRetryProperties.getMaxInterval()).isEqualTo(defaultRetryProperties.getMaxInterval());
			assertThat(configMapRetryProperties.getMultiplier()).isEqualTo(defaultRetryProperties.getMultiplier());

			RetryProperties secretsRetryProperties = secretsConfigProperties.getRetry();

			assertThat(secretsRetryProperties.getMaxAttempts()).isEqualTo(defaultRetryProperties.getMaxAttempts());
			assertThat(secretsRetryProperties.getInitialInterval())
					.isEqualTo(defaultRetryProperties.getInitialInterval());
			assertThat(secretsRetryProperties.getMaxInterval()).isEqualTo(defaultRetryProperties.getMaxInterval());
			assertThat(secretsRetryProperties.getMultiplier()).isEqualTo(defaultRetryProperties.getMultiplier());
		}

	}

	@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class,
			properties = { "spring.cloud.kubernetes.config.fail-fast=true",
					"spring.cloud.kubernetes.config.retry.max-attempts=3",
					"spring.cloud.kubernetes.config.retry.initial-interval=1500",
					"spring.cloud.kubernetes.config.retry.max-interval=3000",
					"spring.cloud.kubernetes.config.retry.multiplier=1.5" })
	@Nested
	public class ConfigMapFailFastEnabledWithCustomRetryConfiguration {

		@Autowired
		ConfigMapConfigProperties configMapConfigProperties;

		@Test
		public void retryConfigurationShouldBeCustomized() {
			RetryProperties retryProperties = configMapConfigProperties.getRetry();

			assertThat(retryProperties.getMaxAttempts()).isEqualTo(3);
			assertThat(retryProperties.getInitialInterval()).isEqualTo(1500L);
			assertThat(retryProperties.getMaxInterval()).isEqualTo(3000L);
			assertThat(retryProperties.getMultiplier()).isEqualTo(1.5D);
		}

	}

	@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = App.class,
			properties = { "spring.cloud.kubernetes.secrets.fail-fast=true",
					"spring.cloud.kubernetes.secrets.retry.max-attempts=3",
					"spring.cloud.kubernetes.secrets.retry.initial-interval=1500",
					"spring.cloud.kubernetes.secrets.retry.max-interval=3000",
					"spring.cloud.kubernetes.secrets.retry.multiplier=1.5" })
	@Nested
	public class SecretsFailFastEnabledWithCustomRetryConfiguration {

		@Autowired
		SecretsConfigProperties secretsConfigProperties;

		@Test
		public void retryConfigurationShouldBeCustomized() {
			RetryProperties retryProperties = secretsConfigProperties.getRetry();

			assertThat(retryProperties.getMaxAttempts()).isEqualTo(3);
			assertThat(retryProperties.getInitialInterval()).isEqualTo(1500L);
			assertThat(retryProperties.getMaxInterval()).isEqualTo(3000L);
			assertThat(retryProperties.getMultiplier()).isEqualTo(1.5D);
		}

	}

	@Nested
	public class FailFastEnabledWithoutSpringRetryOnClasspath {

		private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesBootstrapConfiguration.class))
				.withClassLoader(new FilteredClassLoader(Retryable.class, Aspect.class, AopAutoConfiguration.class));

		@Test
		public void shouldNotDefineRetryBeansWhenConfigMapFailFastEnabled() {
			contextRunner.withPropertyValues("spring.cloud.kubernetes.config.fail-fast=true")
					.run(context -> assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty());
		}

		@Test
		public void shouldNotDefineRetryBeansWhenSecretsFailFastEnabled() {
			contextRunner.withPropertyValues("spring.cloud.kubernetes.secrets.fail-fast=true")
					.run(context -> assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty());
		}

		@Test
		public void shouldNotDefineRetryBeansWhenConfigMapAndSecretsFailFastEnabled() {
			contextRunner
					.withPropertyValues("spring.cloud.kubernetes.config.fail-fast=true",
							"spring.cloud.kubernetes.secrets.fail-fast=true")
					.run(context -> assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty());
		}

	}

	@SpringBootApplication
	static class App {

	}

}
