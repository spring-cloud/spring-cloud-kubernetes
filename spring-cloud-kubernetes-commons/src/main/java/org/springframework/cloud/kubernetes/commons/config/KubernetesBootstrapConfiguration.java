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

import org.aspectj.lang.annotation.Aspect;

import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.NeverRetryPolicy;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnKubernetesEnabled
@EnableConfigurationProperties({ ConfigMapConfigProperties.class, SecretsConfigProperties.class })
public class KubernetesBootstrapConfiguration {

	@ConditionalOnKubernetesConfigPropertiesRetryEnabled
	@ConditionalOnClass({ Retryable.class, Aspect.class, AopAutoConfiguration.class })
	@Configuration(proxyBeanMethods = false)
	@EnableRetry(proxyTargetClass = true)
	@Import(AopAutoConfiguration.class)
	static class RetryConfiguration {

		private static RetryOperationsInterceptor retryOperationsInterceptor(
				AbstractConfigProperties.RetryProperties retryProperties) {
			return RetryInterceptorBuilder.stateless().backOffOptions(retryProperties.getInitialInterval(),
					retryProperties.getMultiplier(), retryProperties.getMaxInterval())
					.maxAttempts(retryProperties.getMaxAttempts()).build();
		}

		@Bean
		@ConditionalOnConfigMapPropertiesRetryEnabled
		public RetryOperationsInterceptor configMapPropertiesRetryInterceptor(
				ConfigMapConfigProperties configProperties) {
			return retryOperationsInterceptor(configProperties.getRetry());
		}

		@Bean("configMapPropertiesRetryInterceptor")
		@ConditionalOnConfigMapPropertiesRetryDisabled
		public RetryOperationsInterceptor configMapPropertiesRetryInterceptorNoRetry() {
			return RetryInterceptorBuilder.stateless().retryPolicy(new NeverRetryPolicy()).build();
		}

		@Bean
		@ConditionalOnSecretsPropertiesRetryEnabled
		public RetryOperationsInterceptor secretsPropertiesRetryInterceptor(SecretsConfigProperties configProperties) {
			return retryOperationsInterceptor(configProperties.getRetry());
		}

		@Bean("secretsPropertiesRetryInterceptor")
		@ConditionalOnSecretsPropertiesRetryDisabled
		public RetryOperationsInterceptor secretsPropertiesRetryInterceptorNoRetry() {
			return RetryInterceptorBuilder.stateless().retryPolicy(new NeverRetryPolicy()).build();
		}

	}

}
