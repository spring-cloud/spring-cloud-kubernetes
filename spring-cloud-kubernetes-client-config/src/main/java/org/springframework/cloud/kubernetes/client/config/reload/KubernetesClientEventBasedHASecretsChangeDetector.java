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

package org.springframework.cloud.kubernetes.client.config.reload;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * HA Secret change detector. Its informers are started by the HA coordinator after
 * leadership is acquired.
 *
 * @author wind57
 */
public final class KubernetesClientEventBasedHASecretsChangeDetector
		extends KubernetesClientAbstractSecretsChangeDetector {

	public KubernetesClientEventBasedHASecretsChangeDetector(ConfigurationUpdateStrategy strategy,
			PropertySourceLocator propertySourceLocator, ConfigurableEnvironment environment, CoreV1Api coreV1Api,
			ConfigReloadProperties properties, KubernetesNamespaceProvider kubernetesNamespaceProvider,
			Class<? extends MapPropertySource> existingSourcesType) {
		super(strategy, propertySourceLocator, environment, coreV1Api, properties, kubernetesNamespaceProvider,
				existingSourcesType);
	}

}
