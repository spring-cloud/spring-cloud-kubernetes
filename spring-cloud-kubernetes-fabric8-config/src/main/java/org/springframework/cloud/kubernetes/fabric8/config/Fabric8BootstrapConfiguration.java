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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesConfigEnabled;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesSecretsEnabled;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
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
@ConditionalOnKubernetesEnabled
@Import({ KubernetesCommonsAutoConfiguration.class, Fabric8AutoConfiguration.class })
@ConditionalOnClass({ ConfigMap.class, Secret.class })
@AutoConfigureAfter(KubernetesBootstrapConfiguration.class)
public class Fabric8BootstrapConfiguration {

	@Bean
	@ConditionalOnKubernetesConfigEnabled
	public Fabric8ConfigMapPropertySourceLocator configMapPropertySourceLocator(ConfigMapConfigProperties properties,
			KubernetesClient client) {
		return new Fabric8ConfigMapPropertySourceLocator(client, properties);
	}

	@Bean
	@ConditionalOnKubernetesSecretsEnabled
	public Fabric8SecretsPropertySourceLocator secretsPropertySourceLocator(SecretsConfigProperties properties,
			KubernetesClient client) {
		return new Fabric8SecretsPropertySourceLocator(client, properties);
	}

}
