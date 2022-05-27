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

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
final class Fabric8ConfigUtils {

	private static final Log LOG = LogFactory.getLog(Fabric8ConfigUtils.class);

	private Fabric8ConfigUtils() {
	}

	/**
	 * this method does the namespace resolution for both config map and secrets
	 * implementations. It tries these places to find the namespace:
	 *
	 * <pre>
	 *     1. from a normalized source (which can be null)
	 *     2. from a property 'spring.cloud.kubernetes.client.namespace', if such is present
	 *     3. from a String residing in a file denoted by `spring.cloud.kubernetes.client.serviceAccountNamespacePath`
	 * 	      property, if such is present
	 * 	   4. from a String residing in `/var/run/secrets/kubernetes.io/serviceaccount/namespace` file,
	 * 	  	  if such is present (kubernetes default path)
	 * 	   5. from KubernetesClient::getNamespace, which is implementation specific.
	 * </pre>
	 *
	 * If any of the above fail, we throw a NamespaceResolutionFailedException.
	 * @param namespace normalized namespace
	 * @param configurationTarget Config Map/Secret
	 * @param provider the provider which computes the namespace
	 * @param client fabric8 Kubernetes client
	 * @return application namespace
	 * @throws NamespaceResolutionFailedException when namespace could not be resolved
	 */
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
				return providerNamespace;
			}
		}

		String clientNamespace = client.getNamespace();
		LOG.debug(configurationTarget + " namespace from client : " + clientNamespace);
		if (clientNamespace == null) {
			throw new NamespaceResolutionFailedException("unresolved namespace");
		}
		return clientNamespace;

	}

	/*
	 * namespace that reaches this point is absolutely present, otherwise this would have
	 * resulted in a NamespaceResolutionFailedException.
	 */
	static Map<String, String> configMapDataByName(KubernetesClient client, String namespace, String name) {
		LOG.debug("Loading ConfigMap with name '" + name + "' in namespace '" + namespace + "'");
		ConfigMap configMap = client.configMaps().inNamespace(namespace).withName(name).get();

		if (configMap == null) {
			LOG.warn("config-map with name : '" + name + "' not present in namespace : '" + namespace + "'");
			return Collections.emptyMap();
		}

		return configMap.getData();
	}

	static Map<String, Object> secretDataByName(KubernetesClient client, String namespace, String name) {
		LOG.debug("Loading Secret with name '" + name + "' in namespace '" + namespace + "'");
		Secret secret = client.secrets().inNamespace(namespace).withName(name).get();

		if (secret == null) {
			LOG.warn("secret with name : '" + name + "' not present in namespace : '" + namespace + "'");
			return Collections.emptyMap();
		}

		return decodeData(secret.getData());
	}

	static Map.Entry<String, Map<String, Object>> secretDataByLabels(KubernetesClient client, String namespace,
			Map<String, String> labels) {
		LOG.debug("Loading Secret with labels '" + labels + "' in namespace '" + namespace + "'");
		List<Secret> secrets = client.secrets().inNamespace(namespace).withLabels(labels).list().getItems();

		if (secrets == null || secrets.isEmpty()) {
			LOG.warn("secret(s) with labels : '" + labels + "' not present in namespace : '" + namespace + "'");
			return Map.entry("", Collections.emptyMap());
		}

		Map<String, Object> result = new HashMap<>();
		for (Secret secret : secrets) {
			// we support prefix per source, not per secret. This means that
			// in theory
			// we can still have clashes here, that we simply override.
			// If there are more than one secret found per labels, and they
			// have the same key on a
			// property, but different values; one value will override the
			// other, without any
			// particular order.
			result.putAll(decodeData(secret.getData()));
		}

		String secretNames = secrets.stream().map(Secret::getMetadata).map(ObjectMeta::getName).sorted()
				.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
		return Map.entry(secretNames, result);
	}

	private static Map<String, Object> decodeData(Map<String, String> data) {
		Map<String, Object> result = new HashMap<>(CollectionUtils.newHashMap(data.size()));
		data.forEach((key, value) -> result.put(key, new String(Base64.getDecoder().decode(value)).trim()));
		return result;
	}

}
