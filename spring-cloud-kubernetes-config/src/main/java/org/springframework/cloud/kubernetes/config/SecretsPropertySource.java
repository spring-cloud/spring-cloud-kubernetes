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

package org.springframework.cloud.kubernetes.config;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Kubernetes property source for secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 */
public class SecretsPropertySource extends MapPropertySource {

	private static final Log LOG = LogFactory.getLog(SecretsPropertySource.class);

	private static final String PREFIX = "secrets";

	public SecretsPropertySource(KubernetesClient client, Environment env, String name, String namespace,
			Map<String, String> labels) {
		super(getSourceName(client, env, name, namespace), getSourceData(client, env, name, namespace, labels));
	}

	private static String getSourceName(KubernetesClient client, Environment env, String name, String namespace) {
		return new StringBuilder().append(PREFIX).append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR).append(name)
				.append(Constants.PROPERTY_SOURCE_NAME_SEPARATOR).append(namespace).toString();
	}

	private static Map<String, Object> getSourceData(KubernetesClient client, Environment env, String name,
			String namespace, Map<String, String> labels) {
		Map<String, Object> result = new HashMap<>();

		try {
			// Read for secrets api (named)
			Secret secret;
			if (StringUtils.isEmpty(namespace)) {
				secret = client.secrets().withName(name).get();
			}
			else {
				secret = client.secrets().inNamespace(namespace).withName(name).get();
			}
			putAll(secret, result);

			// Read for secrets api (label)
			if (!labels.isEmpty()) {
				if (StringUtils.isEmpty(namespace)) {
					client.secrets().withLabels(labels).list().getItems().forEach(s -> putAll(s, result));
				}
				else {
					client.secrets().inNamespace(namespace).withLabels(labels).list().getItems()
							.forEach(s -> putAll(s, result));
				}
			}
		}
		catch (Exception e) {
			LOG.warn("Can't read secret with name: [" + name + "] or labels [" + labels + "] in namespace:[" + namespace
					+ "] (cause: " + e.getMessage() + "). Ignoring");
		}

		return result;
	}

	// *****************************
	// Helpers
	// *****************************
	private static void putAll(Secret secret, Map<String, Object> result) {
		if (secret != null && secret.getData() != null) {
			secret.getData().forEach((k, v) -> result.put(k, new String(Base64.getDecoder().decode(v)).trim()));
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " {name='" + this.name + "'}";
	}

}
