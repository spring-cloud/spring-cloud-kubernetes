/*
 * Copyright 2013-2019 the original author or authors.
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

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.getApplicationNamespace;

/**
 * A {@link PropertySourceLocator} that uses config maps.
 *
 * @author Ioannis Canellos
 * @author Michael Moudatsos
 */
@Order(0)
public class Fabric8ConfigMapPropertySourceLocator extends ConfigMapPropertySourceLocator {

	private final KubernetesClient client;

	private final KubernetesNamespaceProvider provider;

	/**
	 * This constructor is deprecated. Its usage might cause unexpected behavior when
	 * looking for different properties. For example, in general, if a namespace is not
	 * provided, we might look it up via other means: different documented environment
	 * variables or from a kubernetes client itself. Using this constructor might not
	 * reflect that.
	 */
	@Deprecated
	public Fabric8ConfigMapPropertySourceLocator(KubernetesClient client, ConfigMapConfigProperties properties) {
		super(properties);
		this.client = client;
		this.provider = null;
	}

	public Fabric8ConfigMapPropertySourceLocator(KubernetesClient client, ConfigMapConfigProperties properties,
			KubernetesNamespaceProvider provider) {
		super(properties);
		this.client = client;
		this.provider = provider;
	}

	@Override
	protected MapPropertySource getMapPropertySource(String applicationName, NormalizedSource normalizedSource,
			String configurationTarget, ConfigurableEnvironment environment) {
		String namespace = getApplicationNamespace(this.client, normalizedSource.getNamespace(), configurationTarget,
				provider);
		return new Fabric8ConfigMapPropertySource(this.client, applicationName, namespace, environment,
				normalizedSource.getPrefix(), normalizedSource.isUseProfileNameAsSuffix());
	}

}
