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

package org.springframework.cloud.kubernetes.commons.config.configdata;

import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

/**
 * @author wind57
 *
 * holds properties needed for ConfigData.
 */
record Properties(KubernetesClientProperties clientProperties, ConfigMapConfigProperties configMapProperties,
		SecretsConfigProperties secretsConfigProperties) {

	static Properties of(ConfigDataLocationResolverContext context) {
		Binder binder = context.getBinder();

		boolean configEnabled = binder.bind("spring.cloud.kubernetes.config.enabled", boolean.class).orElse(true);
		boolean secretsEnabled = binder.bind("spring.cloud.kubernetes.secrets.enabled", boolean.class).orElse(true);

		ConfigMapConfigProperties configMapConfigProperties = null;
		if (configEnabled) {
			configMapConfigProperties = binder.bindOrCreate(ConfigMapConfigProperties.PREFIX,
					ConfigMapConfigProperties.class);
		}

		SecretsConfigProperties secretsProperties = null;
		if (secretsEnabled) {
			secretsProperties = binder.bindOrCreate(SecretsConfigProperties.PREFIX, SecretsConfigProperties.class);
		}

		String namespace = binder.bind("spring.cloud.kubernetes.client.namespace", String.class)
				.orElse(binder.bind("kubernetes.namespace", String.class).orElse(""));
		KubernetesClientProperties clientProperties = clientProperties(context, namespace);

		return new Properties(clientProperties, configMapConfigProperties, secretsProperties);
	}

	private static KubernetesClientProperties clientProperties(ConfigDataLocationResolverContext context,
			String namespace) {
		KubernetesClientProperties kubernetesClientProperties;
		if (context.getBootstrapContext().isRegistered(KubernetesClientProperties.class)) {
			kubernetesClientProperties = context.getBootstrapContext().get(KubernetesClientProperties.class)
					.withNamespace(namespace);
		}
		else {
			kubernetesClientProperties = context.getBinder()
					.bindOrCreate(KubernetesClientProperties.PREFIX, Bindable.of(KubernetesClientProperties.class))
					.withNamespace(namespace);
		}
		return kubernetesClientProperties;
	}
}
