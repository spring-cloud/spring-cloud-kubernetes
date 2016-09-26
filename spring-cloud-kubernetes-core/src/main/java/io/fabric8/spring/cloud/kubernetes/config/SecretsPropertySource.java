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
package io.fabric8.spring.cloud.kubernetes.config;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class SecretsPropertySource extends MapPropertySource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecretsPropertySource.class);

    private static final String PREFIX = "secrets";

    public SecretsPropertySource(KubernetesClient client, Environment env, SecretsConfigProperties config) {
        super(
            getSourceName(client, env, config),
            getSourceData(client, env, config)
        );
    }

    private static String getSourceName(KubernetesClient client, Environment env, SecretsConfigProperties config) {
        return new StringBuilder()
            .append(PREFIX)
            .append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR)
            .append(getApplicationName(env,config))
            .append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR)
            .append(getApplicationNamespace(client, config))
            .toString();
    }

    private static Map<String, Object> getSourceData(KubernetesClient client, Environment env, SecretsConfigProperties config) {
        String name = getApplicationName(env, config);
        String namespace = getApplicationNamespace(client, config);

        Map<String, Object> result = new HashMap<>();
        try {
            if (config.getLabels().isEmpty()) {
                if (StringUtils.isEmpty(namespace)) {
                    putAll(
                        client.secrets()
                            .withName(name)
                            .get(),
                        result);
                } else {
                    putAll(
                        client.secrets()
                            .inNamespace(namespace)
                            .withName(name)
                            .get(),
                        result);
                }
            } else {
                if (StringUtils.isEmpty(namespace)) {
                    client.secrets()
                        .withLabels(config.getLabels())
                        .list()
                        .getItems()
                        .forEach(s -> putAll(s, result));
                } else {
                    client.secrets()
                        .inNamespace(namespace)
                        .withLabels(config.getLabels())
                        .list()
                        .getItems()
                        .forEach(s -> putAll(s, result));

                }
            }
        } catch (Exception e) {
            LOGGER.warn("Can't read secret with name: [{}] or labels [{}] in namespace:[{}]. Ignoring",
                config.getName(),
                config.getLabels(),
                namespace,
                e);
        }
        return result;
    }

    // *****************************
    // Helpers
    // *****************************

    private static String getApplicationName(Environment env, SecretsConfigProperties config) {
        String name = config.getName();
        if (StringUtils.isEmpty(name)) {
            name = env.getProperty(
                Constants.SPRING_APPLICATION_NAME,
                Constants.FALLBACK_APPLICATION_NAME);
        }

        return name;
    }

    private static String getApplicationNamespace(KubernetesClient client, SecretsConfigProperties config) {
        String namespace = config.getNamespace();
        if (StringUtils.isEmpty(namespace)) {
            namespace = client.getNamespace();
        }

        return namespace;
    }

    private static void putAll(Secret secret, Map<String, Object> result) {
        if (secret != null && secret.getData() != null) {
            secret.getData().forEach((k, v) -> result.put(
                k,
                new String(Base64.getDecoder().decode(v)).trim())
            );
        }
    }
}
