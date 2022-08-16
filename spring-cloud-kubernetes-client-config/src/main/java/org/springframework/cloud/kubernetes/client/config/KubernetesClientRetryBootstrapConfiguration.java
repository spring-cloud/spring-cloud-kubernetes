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

package org.springframework.cloud.kubernetes.client.config;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConditionalOnKubernetesConfigOrSecretsRetryEnabled;
import org.springframework.cloud.kubernetes.commons.config.ConditionalOnKubernetesConfigRetryEnabled;
import org.springframework.cloud.kubernetes.commons.config.ConditionalOnKubernetesSecretsRetryEnabled;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(KubernetesBootstrapConfiguration.class)
@AutoConfigureBefore(KubernetesClientBootstrapConfiguration.class)
@Import({ KubernetesCommonsAutoConfiguration.class, KubernetesClientAutoConfiguration.class })
@ConditionalOnKubernetesConfigOrSecretsRetryEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
public class KubernetesClientRetryBootstrapConfiguration {

	@Bean
	@ConditionalOnKubernetesConfigRetryEnabled
	public KubernetesClientConfigMapPropertySourceLocator retryableConfigMapPropertySourceLocator(
			ConfigMapConfigProperties properties, CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new RetryableKubernetesClientConfigMapPropertySourceLocator(coreV1Api, properties,
				kubernetesNamespaceProvider);
	}

	@Bean
	@ConditionalOnKubernetesSecretsRetryEnabled
	public KubernetesClientSecretsPropertySourceLocator retryableSecretsPropertySourceLocator(
			SecretsConfigProperties properties, CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new RetryableKubernetesClientSecretsPropertySourceLocator(coreV1Api, kubernetesNamespaceProvider,
				properties);
	}

}
