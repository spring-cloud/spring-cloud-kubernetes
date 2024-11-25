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

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author wind57
 */
public class VisibleKubernetesClientEventBasedConfigMapChangeDetector
		extends KubernetesClientEventBasedConfigMapChangeDetector {

	public VisibleKubernetesClientEventBasedConfigMapChangeDetector(CoreV1Api coreV1Api,
			ConfigurableEnvironment environment, ConfigReloadProperties properties,
			ConfigurationUpdateStrategy strategy, KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider);
	}

	public void onEvent(KubernetesObject kubernetesObject) {
		super.onEvent(kubernetesObject);
	}

}
