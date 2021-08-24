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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientConfigMapPropertySource extends ConfigMapPropertySource {

	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigMapPropertySource.class);

	public KubernetesClientConfigMapPropertySource(CoreV1Api coreV1Api, String name, String namespace,
			Environment environment, String prefix, boolean failFast) {
		super(getName(name, namespace), getData(coreV1Api, name, namespace, environment, prefix, failFast));
	}

	private static Map<String, Object> getData(CoreV1Api coreV1Api, String name, String namespace,
			Environment environment, String prefix, boolean failFast) {

		LOG.info("Loading ConfigMap with name '" + name + "' in namespace '" + namespace + "'");
		try {
			Set<String> names = new HashSet<>();
			names.add(name);
			if (environment != null) {
				for (String activeProfile : environment.getActiveProfiles()) {
					names.add(name + "-" + activeProfile);
				}
			}
			Map<String, Object> result = new HashMap<>();
			coreV1Api.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null, null)
					.getItems().stream().filter(cm -> names.contains(cm.getMetadata().getName()))
					.map(map -> processAllEntries(map.getData(), environment)).collect(Collectors.toList())
					.forEach(result::putAll);

			if (!"".equals(prefix)) {
				Map<String, Object> withPrefix = CollectionUtils.newHashMap(result.size());
				result.forEach((key, value) -> withPrefix.put(prefix + "." + key, value));
				return withPrefix;
			}

			return result;
		}
		catch (ApiException e) {
			if (failFast) {
				throw new IllegalStateException(
						"Unable to read ConfigMap with name '" + name + "' in namespace '" + namespace + "'", e);
			}

			LOG.warn("Unable to get ConfigMap " + name + " in namespace " + namespace, e);
		}
		return Collections.emptyMap();
	}

}
