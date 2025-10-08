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

package org.springframework.cloud.kubernetes.commons.configdata;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

/**
 * @author wind57
 */
class StorePropertiesResolver extends KubernetesConfigDataLocationResolver {

	SecretsConfigProperties secretsConfigProperties;

	ConfigMapConfigProperties configMapConfigProperties;

	KubernetesClientProperties kubernetesClientProperties;

	@Override
	protected void registerBeans(ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location,
			Profiles profiles, ConfigDataPropertiesHolder properties, KubernetesNamespaceProvider namespaceProvider) {

		secretsConfigProperties = properties.secretsProperties();
		configMapConfigProperties = properties.configMapProperties();
		kubernetesClientProperties = properties.clientProperties();
	}

}
