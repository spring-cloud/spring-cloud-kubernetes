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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.getApplicationNamespace;

/**
 * Kubernetes {@link PropertySourceLocator} for secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author Isik Erhan
 */
@Order(1)
public class Fabric8SecretsPropertySourceLocator extends SecretsPropertySourceLocator {

	private final KubernetesClient client;

	private final KubernetesNamespaceProvider provider;

	public Fabric8SecretsPropertySourceLocator(KubernetesClient client, SecretsConfigProperties properties,
			KubernetesNamespaceProvider provider) {
		super(properties);
		this.client = client;
		this.provider = provider;
	}

	@Override
	protected MapPropertySource getPropertySource(ConfigurableEnvironment environment,
			SecretsConfigProperties.NormalizedSource normalizedSource, String configurationTarget) {
		String secretName = getApplicationName(environment, normalizedSource.getName(), configurationTarget);
		String secretNamespace = getApplicationNamespace(this.client, normalizedSource.getNamespace(),
				configurationTarget, provider);
		Map<String, String> labels = normalizedSource.getLabels();
		return new Fabric8SecretsPropertySource(this.client, secretName, secretNamespace, labels,
				this.properties.isFailFast());
	}

}
