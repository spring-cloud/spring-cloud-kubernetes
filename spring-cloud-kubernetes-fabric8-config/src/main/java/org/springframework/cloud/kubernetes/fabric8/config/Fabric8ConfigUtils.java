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

import java.util.Collections;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.util.StringUtils;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class Fabric8ConfigUtils {

	private static final Log LOG = LogFactory.getLog(Fabric8ConfigUtils.class);

	private Fabric8ConfigUtils() {
	}

	@Deprecated
	public static String getApplicationNamespace(KubernetesClient client, String namespace,
			String configurationTarget) {
		if (!StringUtils.hasLength(namespace)) {
			LOG.debug(configurationTarget + " namespace has not been set, taking it from client (ns="
					+ client.getNamespace() + ")");
			namespace = client.getNamespace();
		}

		return namespace;
	}

	static String getApplicationNamespace(KubernetesClient client, String namespace, String configurationTarget,
			KubernetesNamespaceProvider provider) {

		if (StringUtils.hasText(namespace)) {
			LOG.debug(configurationTarget + " namespace from normalized source : " + namespace);
			return namespace;
		}

		if (provider != null) {
			String providerNamespace = provider.getNamespace();
			if (StringUtils.hasText(providerNamespace)) {
				LOG.debug(configurationTarget + " namespace from provider : " + namespace);
			}
		}

		LOG.debug(configurationTarget + " namespace from client : " + client.getNamespace());
		return client.getNamespace();

	}

	public static String getApplicationNamespace(KubernetesClient client, String namespace) {
		return !StringUtils.hasLength(namespace) ? client.getNamespace() : namespace;
	}

	public static Map<String, String> getConfigMapData(KubernetesClient client, String namespace, String name) {
		ConfigMap configMap = !StringUtils.hasLength(namespace) ? client.configMaps().withName(name).get()
				: client.configMaps().inNamespace(namespace).withName(name).get();

		if (configMap == null) {
			LOG.warn("config-map with name : '" + name + "' not present in namespace : '" + namespace + "'");
			return Collections.emptyMap();
		}

		return configMap.getData();
	}

}
