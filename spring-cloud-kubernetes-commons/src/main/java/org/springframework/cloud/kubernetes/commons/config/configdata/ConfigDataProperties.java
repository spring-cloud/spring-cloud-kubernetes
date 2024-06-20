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

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.registerSingle;

public record ConfigDataProperties(KubernetesClientProperties clientProperties,
		ConfigMapConfigProperties configMapProperties, SecretsConfigProperties secretsProperties) {

	static Registrar of(ConfigDataLocationResolverContext context) {
		Properties all = Properties.of(context);
		return () -> {
			registerProperties(context, all.clientProperties(), all.configMapProperties(),
					all.secretsConfigProperties());
			return new ConfigDataProperties(all.clientProperties(), all.configMapProperties(),
					all.secretsConfigProperties());
		};
	}

	private static void registerProperties(ConfigDataLocationResolverContext resolverContext,
			KubernetesClientProperties clientProperties, ConfigMapConfigProperties configMapProperties,
			SecretsConfigProperties secretsProperties) {

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		registerSingle(bootstrapContext, KubernetesClientProperties.class, clientProperties,
				"configDataKubernetesClientProperties");

		registerSingle(bootstrapContext, ConfigMapConfigProperties.class, configMapProperties,
				"configDataConfigMapConfigProperties");

		registerSingle(bootstrapContext, SecretsConfigProperties.class, secretsProperties,
				"configDataSecretsConfigProperties");
	}

	@FunctionalInterface
	interface Registrar {

		ConfigDataProperties register();

	}
}
