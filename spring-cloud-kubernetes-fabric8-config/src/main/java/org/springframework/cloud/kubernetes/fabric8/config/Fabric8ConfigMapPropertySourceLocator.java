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

	public Fabric8ConfigMapPropertySourceLocator(KubernetesClient client, ConfigMapConfigProperties properties) {
		super(properties);
		this.client = client;
	}

	@Override
	protected MapPropertySource getMapPropertySource(String applicationName, NormalizedSource normalizedSource,
			String configurationTarget, ConfigurableEnvironment environment) {
		String namespaceName = getApplicationNamespace(this.client, normalizedSource.getNamespace(),
				configurationTarget);
		return new Fabric8ConfigMapPropertySource(this.client, applicationName, namespaceName, environment);
	}

}
