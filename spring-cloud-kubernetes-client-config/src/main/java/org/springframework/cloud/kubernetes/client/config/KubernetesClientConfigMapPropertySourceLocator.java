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

import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySourceLocator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.getNamespace;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientConfigMapPropertySourceLocator extends ConfigMapPropertySourceLocator {

	private CoreV1Api coreV1Api;

	private KubernetesClientProperties kubernetesClientProperties;

	private KubernetesNamespaceProvider kubernetesNamespaceProvider;

	public KubernetesClientConfigMapPropertySourceLocator(CoreV1Api coreV1Api, ConfigMapConfigProperties properties,
			KubernetesClientProperties kubernetesClientProperties) {
		super(properties);
		this.coreV1Api = coreV1Api;
		this.kubernetesClientProperties = kubernetesClientProperties;
	}

	public KubernetesClientConfigMapPropertySourceLocator(CoreV1Api coreV1Api, ConfigMapConfigProperties properties,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(properties);
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	@Override
	protected MapPropertySource getMapPropertySource(String name,
			ConfigMapConfigProperties.NormalizedSource normalizedSource, String configurationTarget,
			ConfigurableEnvironment environment) {
		String fallbackNamespace = kubernetesNamespaceProvider != null ? kubernetesNamespaceProvider.getNamespace()
				: kubernetesClientProperties.getNamespace();
		return new KubernetesClientConfigMapPropertySource(coreV1Api, name,
				getNamespace(normalizedSource, fallbackNamespace), environment);
	}

}
