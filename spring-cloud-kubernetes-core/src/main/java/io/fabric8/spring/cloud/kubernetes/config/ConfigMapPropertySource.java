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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigMapPropertySource extends MapPropertySource {

    private static final String PREFIX = "configmap";
    private static final String SEPARATOR = ".";

    private final KubernetesClient client;
    private final String name;
    private final String namespace;

    public ConfigMapPropertySource(KubernetesClient client, String name) {
        this(client, name, null);
    }

    public ConfigMapPropertySource(KubernetesClient client, String name, String namespace) {
        super(getName(client, name, namespace), asObjectMap(getData(client, name, namespace)));
        this.client = client;
        this.name = name;
        this.namespace = namespace;
    }

    private static String getName(KubernetesClient client, String name, String namespace) {
        StringBuilder sb = new StringBuilder();
        sb.append(PREFIX).append(SEPARATOR).append(name).append(SEPARATOR).append(namespace == null || namespace.isEmpty() ? client.getNamespace() : namespace);
        return sb.toString();
    }

    private static Map<String, String> getData(KubernetesClient client, String name, String namespace) {
        ConfigMap map = namespace == null || namespace.isEmpty()
                ? client.configMaps().withName(name).get()
                : client.configMaps().inNamespace(namespace).withName(name).get();

        return map != null ? map.getData() : Collections.emptyMap();
    }

    private static Map<String, Object> asObjectMap(Map<String, String> source) {
        return source.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
