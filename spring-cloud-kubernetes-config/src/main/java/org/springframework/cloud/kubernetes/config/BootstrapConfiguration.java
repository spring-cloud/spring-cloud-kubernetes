/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto configuration that reuses Kubernetes config maps as property sources.
 *
 * @author Ioannis Canellos
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.cloud.kubernetes.enabled", matchIfMissing = true)
@ConditionalOnClass({ ConfigMap.class, Secret.class })
public class BootstrapConfiguration {

	@Configuration(proxyBeanMethods = false)
	@Import({ KubernetesCommonsAutoConfiguration.class, Fabric8AutoConfiguration.class })
	@EnableConfigurationProperties({ ConfigMapConfigProperties.class, SecretsConfigProperties.class })
	protected static class KubernetesPropertySourceConfiguration {

		@Autowired
		private KubernetesClient client;

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.kubernetes.config.enabled", matchIfMissing = true)
		public ConfigMapPropertySourceLocator configMapPropertySourceLocator(ConfigMapConfigProperties properties) {
			return new ConfigMapPropertySourceLocator(this.client, properties);
		}

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.kubernetes.secrets.enabled", matchIfMissing = true)
		public SecretsPropertySourceLocator secretsPropertySourceLocator(SecretsConfigProperties properties) {
			return new SecretsPropertySourceLocator(this.client, properties);
		}

	}

}
