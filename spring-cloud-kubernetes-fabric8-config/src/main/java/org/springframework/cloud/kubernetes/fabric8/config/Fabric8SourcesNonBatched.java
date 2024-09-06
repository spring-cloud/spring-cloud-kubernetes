/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.strippedConfigMaps;

/**
 * non batch reads (not reading in the whole namespace) of configmaps
 * and secrets.
 *
 * @author wind57
 */
final class Fabric8SourcesNonBatched {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8SourcesNonBatched.class));

	private Fabric8SourcesNonBatched() {

	}

	/**
	 * read configmaps by name, one by one, without caching them.
	 */
	static List<StrippedSourceContainer> byName(KubernetesClient client, String namespace,
			LinkedHashSet<String> sourceNames) {

		List<ConfigMap> configMaps = new ArrayList<>(sourceNames.size());

		for (String sourceName : sourceNames) {
			ConfigMap configMap = client.configMaps().inNamespace(namespace).withName(sourceName).get();
			if (configMap != null) {
				LOG.debug("Loaded config map '" + sourceName + "'");
				configMaps.add(configMap);
			}
		}

		return strippedConfigMaps(configMaps);
	}

	/**
	 * read configmaps by labels, without caching them.
	 */
	static List<StrippedSourceContainer> byLabels(KubernetesClient client, String namespace,
			Map<String, String> labels) {

		List<ConfigMap> configMaps = client.configMaps()
			.inNamespace(namespace)
			.withLabels(labels)
			.list()
			.getItems();

		return strippedConfigMaps(configMaps);
	}

}
