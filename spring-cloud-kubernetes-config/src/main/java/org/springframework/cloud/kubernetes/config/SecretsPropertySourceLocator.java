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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Order(1)
public class SecretsPropertySourceLocator implements PropertySourceLocator {
    private final KubernetesClient client;
    private final SecretsConfigProperties properties;

    public SecretsPropertySourceLocator(KubernetesClient client, SecretsConfigProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public MapPropertySource locate(Environment environment) {
        return environment instanceof ConfigurableEnvironment
            ? new SecretsPropertySource(client, environment, properties)
            : null;
    }
}
