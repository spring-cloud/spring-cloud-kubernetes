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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientSecretsPropertySource extends SecretsPropertySource {

	private static final Log LOG = LogFactory.getLog(KubernetesClientSecretsPropertySource.class);

	private CoreV1Api coreV1Api;

	public KubernetesClientSecretsPropertySource(CoreV1Api coreV1Api, String name, String namespace,
			Environment environment, Map<String, String> labels, boolean useNameAsPrefix) {
		super(getSourceName(name, namespace),
				getSourceData(coreV1Api, environment, name, namespace, labels, useNameAsPrefix));

	}

	private static Map<String, Object> getSourceData(CoreV1Api api, Environment env, String name, String namespace,
			Map<String, String> labels, boolean useNameAsPrefix) {
		Map<String, Object> result = new HashMap<>();

		try {
			// Read for secrets api (named)
			if (StringUtils.hasText(name)) {
				Optional<V1Secret> secret;
				if (!StringUtils.hasText(namespace)) {

					// There could technically be more than one, just return the first
					secret = api.listSecretForAllNamespaces(null, null, null, null, null, null, null, null, null, null)
							.getItems().stream().filter(s -> name.equals(s.getMetadata().getName())).findFirst();
				}
				else {
					secret = api
							.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null, null)
							.getItems().stream().filter(s -> name.equals(s.getMetadata().getName())).findFirst();
				}

				secret.ifPresent(s -> putAll(s, result, useNameAsPrefix));
			}

			// Read for secrets api (label)
			if (labels != null && !labels.isEmpty()) {
				if (!StringUtils.hasText(namespace)) {
					api.listSecretForAllNamespaces(null, null, null, createLabelsSelector(labels), null, null, null,
							null, null, null).getItems().forEach(s -> putAll(s, result, useNameAsPrefix));
				}
				else {
					api.listNamespacedSecret(namespace, null, null, null, null, createLabelsSelector(labels), null,
							null, null, null, null).getItems().forEach(s -> putAll(s, result, useNameAsPrefix));
				}
			}
		}
		catch (Exception e) {
			LOG.warn("Can't read secret with name: [" + name + "] or labels [" + labels + "] in namespace:[" + namespace
					+ "] (cause: " + e.getMessage() + "). Ignoring", e);
		}

		return result;
	}

	private static String createLabelsSelector(Map<String, String> labels) {
		StringBuilder selectorString = new StringBuilder();
		for (String key : labels.keySet()) {
			if (selectorString.length() != 0) {
				selectorString.append(",");
			}
			selectorString.append(key + "=" + labels.get(key));
		}
		return selectorString.toString();
	}

	private static void putAll(V1Secret secret, Map<String, Object> result, boolean useNameAsPrefix) {
		Map<String, String> secretData = new HashMap<>();
		secret.getData().forEach((key, value) -> secretData.put(key, Base64.getEncoder().encodeToString(value)));
		if (secret != null) {

			String secretName = null;
			if (secret.getMetadata() != null) {
				secretName = secret.getMetadata().getName();
			}

			putAll(secretData, result, secretName, useNameAsPrefix);
		}
	}

}
