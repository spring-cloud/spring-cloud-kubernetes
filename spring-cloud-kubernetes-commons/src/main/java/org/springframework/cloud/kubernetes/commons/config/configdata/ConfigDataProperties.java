/*
 * Copyright 2013-present the original author or authors.
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

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.registerSingle;

/**
 * @author wind57
 */
public record ConfigDataProperties(KubernetesClientProperties clientProperties,
		ConfigMapConfigProperties configMapProperties, SecretsConfigProperties secretsProperties) {

	static ConfigDataProperties of(ConfigDataLocationResolverContext context) {

		KubernetesClientProperties clientProperties;
		ConfigMapConfigProperties configMapProperties = null;
		SecretsConfigProperties secretsProperties = null;

		Binder binder = context.getBinder();

		boolean configEnabled = binder.bind("spring.cloud.kubernetes.config.enabled", boolean.class).orElse(true);
		if (configEnabled) {
			configMapProperties = binder.bindOrCreate(ConfigMapConfigProperties.PREFIX,
					ConfigMapConfigProperties.class);
		}

		boolean secretsEnabled = binder.bind("spring.cloud.kubernetes.secrets.enabled", boolean.class).orElse(true);
		if (secretsEnabled) {
			secretsProperties = binder.bindOrCreate(SecretsConfigProperties.PREFIX, SecretsConfigProperties.class);
		}

		String namespace = binder.bind("spring.cloud.kubernetes.client.namespace", String.class)
			.orElse(binder.bind("kubernetes.namespace", String.class).orElse(""));
		clientProperties = clientProperties(context, namespace);

		registerProperties(context, clientProperties, configMapProperties, secretsProperties);
		return new ConfigDataProperties(clientProperties, configMapProperties, secretsProperties);
	}

	private static void registerProperties(ConfigDataLocationResolverContext resolverContext,
			KubernetesClientProperties clientProperties, ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsProperties) {

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();

		registerSingle(bootstrapContext, KubernetesClientProperties.class, clientProperties,
				"configDataKubernetesClientProperties");

		if (configMapProperties != null) {
			registerSingle(bootstrapContext, ConfigMapConfigProperties.class, configMapProperties,
					"configDataConfigMapConfigProperties");
		}

		if (secretsProperties != null) {
			registerSingle(bootstrapContext, SecretsConfigProperties.class, secretsProperties,
					"configDataSecretsConfigProperties");
		}

	}

	private static KubernetesClientProperties clientProperties(ConfigDataLocationResolverContext context,
			String namespace) {
		KubernetesClientProperties kubernetesClientProperties;
		if (context.getBootstrapContext().isRegistered(KubernetesClientProperties.class)) {
			kubernetesClientProperties = context.getBootstrapContext()
				.get(KubernetesClientProperties.class)
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
