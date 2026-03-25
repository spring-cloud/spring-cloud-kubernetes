/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver;

import java.util.List;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * @author Ryan Baxter
 */
public interface KubernetesPropertySourceSupplier {

	List<MapPropertySource> get(CoreV1Api coreV1Api, String name, String namespace, Environment environment);

	/**
	 * Splits the namespace string from config server properties into a list of
	 * namespaces. If the configured string contains comma-separated values, those values
	 * are returned. Otherwise, the namespace provided by the namespace provider is
	 * returned as a fallback.
	 * @param namespacesStringFromConfigServerProperties the namespace string from config
	 * server properties
	 * @param namespaceFromNamespaceProvider the fallback namespace from the namespace
	 * provider
	 * @return a list of namespaces from the config server properties, or the fallback
	 * namespace if none are available
	 */
	static List<String> namespaceSplitter(String namespacesStringFromConfigServerProperties,
			String namespaceFromNamespaceProvider) {
		if (!namespacesStringFromConfigServerProperties.isBlank()) {
			String[] namespacesFromConfigServerProperties = namespacesStringFromConfigServerProperties.split(",");
			if (namespacesFromConfigServerProperties.length > 0) {
				return List.of(namespacesFromConfigServerProperties);
			}
		}
		return List.of(namespaceFromNamespaceProvider);
	}

}
