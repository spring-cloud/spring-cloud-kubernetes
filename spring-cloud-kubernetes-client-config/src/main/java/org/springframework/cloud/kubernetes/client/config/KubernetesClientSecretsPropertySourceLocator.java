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

import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
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

	private final KubernetesClientProperties kubernetesClientProperties;

	private final KubernetesNamespaceProvider kubernetesNamespaceProvider;

	private final SecretsConfigProperties properties;

	/**
	 * This constructor is deprecated. Its usage might cause unexpected behavior when
	 * looking for different properties. For example, in general, if a namespace is not
	 * provided, we might look it up via other means: different documented environment
	 * variables or from a kubernetes client itself. Using this constructor might not
	 * reflect that.
	 */
	@Deprecated
	public KubernetesClientSecretsPropertySourceLocator(CoreV1Api coreV1Api,
			KubernetesClientProperties kubernetesClientProperties, SecretsConfigProperties secretsConfigProperties) {
		super(secretsConfigProperties);
		this.coreV1Api = coreV1Api;
		this.kubernetesClientProperties = kubernetesClientProperties;
		this.kubernetesNamespaceProvider = null;
		this.properties = secretsConfigProperties;
	}

	public KubernetesClientSecretsPropertySourceLocator(CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider, SecretsConfigProperties secretsConfigProperties) {
		super(secretsConfigProperties);
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
		this.kubernetesClientProperties = null;
		this.properties = secretsConfigProperties;
	}

	@Override
	protected MapPropertySource getPropertySource(ConfigurableEnvironment environment,
			NormalizedSource normalizedSource, String configurationTarget) {

		String namespace;
		String normalizedNamespace = normalizedSource.getNamespace();

		if (StringUtils.hasText(normalizedNamespace)) {
			namespace = normalizedNamespace;
		}
		else if (kubernetesClientProperties != null) {
			if (StringUtils.hasText(kubernetesClientProperties.getNamespace())) {
				namespace = kubernetesClientProperties.getNamespace();
			}
			else {
				throw new NamespaceResolutionFailedException(
						"could not resolve namespace in normalized source or KubernetesClientProperties");
			}
		}
		else {
			namespace = KubernetesClientConfigUtils.getApplicationNamespace(normalizedNamespace,
				properties.getConfigurationTarget(), kubernetesNamespaceProvider);
		}

		KubernetesClientConfigContext context = new KubernetesClientConfigContext(
			coreV1Api, properties.isFailFast(), normalizedSource, properties.getConfigurationTarget(), namespace
		);

		return new KubernetesClientSecretsPropertySource(context);
	}

}
