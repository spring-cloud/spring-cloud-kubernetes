/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientSecretsPropertySourceLocator extends SecretsPropertySourceLocator {

	private final CoreV1Api coreV1Api;

	private final KubernetesNamespaceProvider kubernetesNamespaceProvider;

	public KubernetesClientSecretsPropertySourceLocator(CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider, SecretsConfigProperties secretsConfigProperties) {
		super(secretsConfigProperties);
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	@Override
	protected MapPropertySource getPropertySource(ConfigurableEnvironment environment, NormalizedSource source) {

		String namespace;
		String normalizedNamespace = source.namespace();

		if (StringUtils.hasText(normalizedNamespace)) {
			namespace = normalizedNamespace;
		}
		else {
			namespace = KubernetesClientConfigUtils.getApplicationNamespace(normalizedNamespace,
					source.target(), kubernetesNamespaceProvider);
		}

		KubernetesClientConfigContext context = new KubernetesClientConfigContext(coreV1Api, source, namespace, environment);

		return new KubernetesClientSecretsPropertySource(context);
	}

}
