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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.CollectionUtils;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.getApplicationNamespace;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.getConfigMapData;

/**
 * A {@link MapPropertySource} that uses Kubernetes config maps.
 *
 * @author Ioannis Canellos
 * @author Ali Shahbour
 * @author Michael Moudatsos
 * @author Isik Erhan
 */
public class Fabric8ConfigMapPropertySource extends ConfigMapPropertySource {

	private static final Log LOG = LogFactory.getLog(Fabric8ConfigMapPropertySource.class);

	public Fabric8ConfigMapPropertySource(KubernetesClient client, String name, String namespace,
			Environment environment, String prefix, boolean includeProfileSpecificSources, boolean failFast) {
		super(getName(name, getApplicationNamespace(client, namespace, "Config Map", null)),
				getData(client, name, getApplicationNamespace(client, namespace, "Config Map", null), environment,
						prefix, includeProfileSpecificSources, failFast));
	}

	private static Map<String, Object> getData(KubernetesClient client, String name, String namespace,
			Environment environment, String prefix, boolean includeProfileSpecificSources, boolean failFast) {

		LOG.info("Loading ConfigMap with name '" + name + "' in namespace '" + namespace + "'");
		try {
			Map<String, String> data = getConfigMapData(client, namespace, name);
			Map<String, Object> result = new HashMap<>(processAllEntries(data, environment));

			if (environment != null && includeProfileSpecificSources) {
				for (String activeProfile : environment.getActiveProfiles()) {
					String mapNameWithProfile = name + "-" + activeProfile;
					Map<String, String> dataWithProfile = getConfigMapData(client, namespace, mapNameWithProfile);
					result.putAll(processAllEntries(dataWithProfile, environment));
				}
			}

			if (!"".equals(prefix)) {
				Map<String, Object> withPrefix = CollectionUtils.newHashMap(result.size());
				result.forEach((key, value) -> withPrefix.put(prefix + "." + key, value));
				return withPrefix;
			}

			return result;

		}
		catch (Exception e) {
			if (failFast) {
				throw new IllegalStateException(
						"Unable to read ConfigMap with name '" + name + "' in namespace '" + namespace + "'", e);
			}

			LOG.warn("Can't read configMap with name: [" + name + "] in namespace: [" + namespace + "]. Ignoring.", e);
		}

		return Collections.emptyMap();
	}

}
