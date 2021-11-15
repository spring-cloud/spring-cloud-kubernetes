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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.getApplicationNamespace;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientSecretsPropertySource extends SecretsPropertySource {

	private static final Log LOG = LogFactory.getLog(KubernetesClientSecretsPropertySource.class);

	public KubernetesClientSecretsPropertySource(CoreV1Api coreV1Api, String name, String namespace,
			Map<String, String> labels, boolean failFast) {
		super(getSourceName(name, getApplicationNamespace(namespace, "Secret", null)),
				getSourceData(coreV1Api, name, getApplicationNamespace(namespace, "Secret", null), labels, failFast));

	}

	private static Map<String, Object> getSourceData(CoreV1Api api, String name, String namespace,
			Map<String, String> labels, boolean failFast) {
		Map<String, Object> result = new HashMap<>();

		try {
			if (StringUtils.hasText(name)) {
				LOG.info("Loading Secret with name '" + name + "'in namespace '" + namespace + "'");
				Optional<V1Secret> secret;
				secret = api.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null, null)
						.getItems().stream().filter(s -> name.equals(s.getMetadata().getName())).findFirst();

				secret.ifPresent(s -> putAll(s, result));
			}

			// Read for secrets api (label)
			if (labels != null && !labels.isEmpty()) {
				LOG.info("Loading Secret with labels '" + labels + "'in namespace '" + namespace + "'");
				api.listNamespacedSecret(namespace, null, null, null, null, createLabelsSelector(labels), null, null,
						null, null, null).getItems().forEach(s -> putAll(s, result));
			}
		}
		catch (Exception e) {
			if (failFast) {
				throw new IllegalStateException("Unable to read Secret with name '" + name + "' or labels [" + labels
						+ "] in namespace '" + namespace + "'", e);
			}

			LOG.warn("Can't read secret with name: [" + name + "] or labels [" + labels + "] in namespace:[" + namespace
					+ "] (cause: " + e.getMessage() + "). Ignoring", e);
		}

		return result;
	}

	private static String createLabelsSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
	}

	private static void putAll(V1Secret secret, Map<String, Object> result) {
		Map<String, String> secretData = new HashMap<>();
		if (secret.getData() != null) {
			secret.getData().forEach((key, value) -> secretData.put(key, Base64.getEncoder().encodeToString(value)));
			putAll(secretData, result);
		}
	}

}
