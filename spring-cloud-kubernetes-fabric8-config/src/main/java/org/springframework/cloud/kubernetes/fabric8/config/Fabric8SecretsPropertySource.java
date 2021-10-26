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

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;
import org.springframework.util.StringUtils;

/**
 * Kubernetes property source for secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author Isik Erhan
 */
public class Fabric8SecretsPropertySource extends SecretsPropertySource {

	private static final Log LOG = LogFactory.getLog(Fabric8SecretsPropertySource.class);

	public Fabric8SecretsPropertySource(KubernetesClient client, String name, String namespace,
			Map<String, String> labels, boolean failFast) {
		super(getSourceName(name, namespace), getSourceData(client, name, namespace, labels, failFast));
	}

	private static Map<String, Object> getSourceData(KubernetesClient client, String name, String namespace,
			Map<String, String> labels, boolean failFast) {
		Map<String, Object> result = new HashMap<>();

		String namespaceToUse = StringUtils.hasLength(namespace) ? namespace : client.getNamespace();
		LOG.info("Loading Secret with name '" + name + "' or with labels [" + labels + "] in namespace '"
				+ namespaceToUse + "'");
		try {

			Secret secret = client.secrets().inNamespace(namespaceToUse).withName(name).get();

			// the API is documented that it might return null
			if (secret == null) {
				LOG.warn("secret with name : " + name + " in namespace : " + namespaceToUse + " not found");
			}
			else {
				putDataFromSecret(secret, result, namespaceToUse);
			}

			client.secrets().inNamespace(namespaceToUse).withLabels(labels).list().getItems()
					.forEach(s -> putDataFromSecret(s, result, namespaceToUse));

		}
		catch (Exception e) {
			if (failFast) {
				throw new IllegalStateException("Unable to read Secret with name '" + name + "' or labels [" + labels
						+ "] in namespace '" + namespaceToUse + "'", e);
			}

			LOG.warn("Can't read secret with name: [" + name + "] or labels [" + labels + "] in namespace: ["
					+ namespaceToUse + "] (cause: " + e.getMessage() + "). Ignoring");
		}

		return result;
	}

	private static void putDataFromSecret(Secret secret, Map<String, Object> result, String namespace) {
		LOG.debug("reading secret with name : " + secret.getMetadata().getName() + " in namespace : " + namespace);
		putAll(secret.getData(), result);
	}

}
