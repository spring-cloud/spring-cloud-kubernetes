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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientConfigUtils {

	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigUtils.class);

	private KubernetesClientConfigUtils() {
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
	 * </pre>
	 *
	 * If any of the above fail, we throw a NamespaceResolutionFailedException.
	 * @param namespace normalized namespace
	 * @param configurationTarget Config Map/Secret
	 * @param provider the provider which computes the namespace
	 * @return application namespace
	 * @throws NamespaceResolutionFailedException when namespace could not be resolved
	 */
	static String getApplicationNamespace(String namespace, String configurationTarget,
			KubernetesNamespaceProvider provider) {
		if (StringUtils.hasText(namespace)) {
			LOG.debug(configurationTarget + " namespace from normalized source or passed directly : " + namespace);
			return namespace;
		}

		if (provider != null) {
			String providerNamespace = provider.getNamespace();
			if (StringUtils.hasText(providerNamespace)) {
				LOG.debug(configurationTarget + " namespace from provider : " + namespace);
				return providerNamespace;
			}
		}

		throw new NamespaceResolutionFailedException("unresolved namespace");
	}

	/*
	 * namespace that reaches this point is absolutely present, otherwise this would have
	 * resulted in a NamespaceResolutionFailedException
	 */
	static Map<String, String> getConfigMapData(CoreV1Api client, String namespace, String name) {
		V1ConfigMap configMap;
		try {
			configMap = client.readNamespacedConfigMap(name, namespace, null, null, null);
		}
		catch (ApiException e) {
			if (e.getCode() == 404) {
				LOG.warn("can't read configmap with name : '" + name + "' in namespace : '" + namespace + "'", e);
				return Collections.emptyMap();
			}

			throw new RuntimeException(e);
		}

		return configMap.getData();
	}

	/**
	 * return decoded data from a secret within a namespace.
	 */
	static Map<String, Object> dataFromSecret(V1Secret secret, String namespace) {
		LOG.debug("reading secret with name : " + secret.getMetadata().getName() + " in namespace : " + namespace);
		Map<String, byte[]> data = secret.getData();

		Map<String, Object> result = new HashMap<>(CollectionUtils.newHashMap(data.size()));
		data.forEach((k, v) -> {
			String decodedValue = decoded(v);
			result.put(k, decodedValue);
		});

		return result;
	}

	private static String decoded(byte[] value) {
		return new String(Base64.getDecoder().decode(Base64.getEncoder().encodeToString(value))).trim();
	}

}
