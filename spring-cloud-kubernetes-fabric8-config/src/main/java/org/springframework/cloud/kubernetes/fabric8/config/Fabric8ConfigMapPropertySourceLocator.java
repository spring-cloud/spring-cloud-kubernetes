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

import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.Fabric8Utils.getApplicationNamespace;

/**
 * A {@link PropertySourceLocator} that uses config maps.
 *
 * @author Ioannis Canellos
 * @author Michael Moudatsos
 * @author Isik Erhan
 */
@Order(0)
public class Fabric8ConfigMapPropertySourceLocator extends ConfigMapPropertySourceLocator {

	private final KubernetesClient client;

	private final KubernetesNamespaceProvider provider;

	Fabric8ConfigMapPropertySourceLocator(KubernetesClient client, ConfigMapConfigProperties properties,
			KubernetesNamespaceProvider provider) {
		super(properties, new Fabric8ConfigMapsCache());
		this.client = client;
		this.provider = provider;
	}

	@Override
	protected MapPropertySource getMapPropertySource(NormalizedSource normalizedSource,
			ConfigurableEnvironment environment) {
		// NormalizedSource has a namespace, but users can skip it.
		// In such cases we try to get it elsewhere
		String namespace = getApplicationNamespace(this.client, normalizedSource.namespace().orElse(null),
				normalizedSource.target(), provider);
		Fabric8ConfigContext context = new Fabric8ConfigContext(client, normalizedSource, namespace, environment);
		return new Fabric8ConfigMapPropertySource(context);
	}

}
