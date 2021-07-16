/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.core.env.Environment;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientConfigMapPropertySource extends ConfigMapPropertySource {

	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigMapPropertySource.class);

	public KubernetesClientConfigMapPropertySource(CoreV1Api coreV1Api, String name, String namespace,
			Environment environment) {
		super(getName(name, namespace), getData(coreV1Api, name, namespace, environment));
	}

	private static Map<String, Object> getData(CoreV1Api coreV1Api, String name, String namespace,
			Environment environment) {

		try {
			List<String> names = new ArrayList<>();
			names.add(name);
			if (environment != null) {
				for (String activeProfile : environment.getActiveProfiles()) {
					names.add(name + "-" + activeProfile);
				}
			}
			Map<String, Object> result = new LinkedHashMap<>();
			coreV1Api.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null, null)
					.getItems().stream().filter(cm -> names.contains(cm.getMetadata().getName()))
					.forEach(map -> result.putAll(processAllEntries(map.getData(), environment)));

			return result;
		}
		catch (ApiException e) {
			LOG.warn("Unable to get ConfigMap " + name + " in namespace " + namespace, e);
		}
		return Collections.emptyMap();
	}

}
