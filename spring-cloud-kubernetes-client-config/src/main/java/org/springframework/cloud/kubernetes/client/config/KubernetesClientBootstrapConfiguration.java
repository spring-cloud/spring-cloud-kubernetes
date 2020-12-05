/*
 * Copyright 2013-2020 the original author or authors.
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
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
@ConditionalOnKubernetesEnabled
@AutoConfigureAfter(KubernetesBootstrapConfiguration.class)
public class KubernetesClientBootstrapConfiguration {

	@Configuration(proxyBeanMethods = false)
	@Import({ KubernetesCommonsAutoConfiguration.class, KubernetesClientAutoConfiguration.class })
	protected static class KubernetesPropertySourceConfiguration {

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.kubernetes.config.enabled")
		public KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator(
				ConfigMapConfigProperties properties, CoreV1Api coreV1Api,
				KubernetesClientProperties kubernetesClientProperties) {
			return new KubernetesClientConfigMapPropertySourceLocator(coreV1Api, properties,
					kubernetesClientProperties);
		}

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.kubernetes.secrets.enabled")
		public KubernetesClientSecretsPropertySourceLocator secretsPropertySourceLocator(
				SecretsConfigProperties properties, CoreV1Api coreV1Api,
				KubernetesClientProperties kubernetesClientProperties) {
			return new KubernetesClientSecretsPropertySourceLocator(coreV1Api, kubernetesClientProperties, properties);
		}

	}

}
