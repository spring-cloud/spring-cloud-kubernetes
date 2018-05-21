/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import static org.springframework.cloud.kubernetes.config.ConfigUtils.getApplicationName;
import static org.springframework.cloud.kubernetes.config.ConfigUtils.getApplicationNamespace;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.kubernetes.config.ConfigMapConfigProperties.NormalizedSource;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Order(0)
public class ConfigMapPropertySourceLocator implements PropertySourceLocator {
    private final KubernetesClient client;
    private final ConfigMapConfigProperties properties;

    public ConfigMapPropertySourceLocator(KubernetesClient client, ConfigMapConfigProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PropertySource locate(Environment environment) {
        if (environment instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment env = (ConfigurableEnvironment) environment;

			List<ConfigMapConfigProperties.NormalizedSource> sources =
				properties.determineSources();
			if (sources.size() == 1) {
				return getMapPropertySourceForSingleConfigMap(env, sources.get(0));
			}

			CompositePropertySource composite = new CompositePropertySource("composite-configmap");
			sources.forEach(s ->
				composite.addFirstPropertySource(getMapPropertySourceForSingleConfigMap(env, s))
			);

			return composite;
		}
        return null;
    }

	private MapPropertySource getMapPropertySourceForSingleConfigMap(
		ConfigurableEnvironment environment, NormalizedSource normalizedSource) {

    	String configurationTarget = properties.getConfigurationTarget();
		return new ConfigMapPropertySource(
			client,
			getApplicationName(environment, normalizedSource.getName(), configurationTarget),
			getApplicationNamespace(client, normalizedSource.getNamespace(), configurationTarget),
			environment.getActiveProfiles(),
			properties
		);
	}
}
