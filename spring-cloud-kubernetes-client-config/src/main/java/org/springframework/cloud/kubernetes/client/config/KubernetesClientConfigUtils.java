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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientConfigUtils {

	private static final Log LOG = LogFactory.getLog(KubernetesClientConfigUtils.class);

	private KubernetesClientConfigUtils() {
	}

	@Deprecated
	public static String getNamespace(ConfigMapConfigProperties.NormalizedSource normalizedSource,
			KubernetesClientProperties kubernetesClientProperties) {
		if (!StringUtils.hasText(normalizedSource.getNamespace())) {
			return kubernetesClientProperties.getNamespace();
		}
		else {
			return normalizedSource.getNamespace();
		}
	}

	@Deprecated
	public static String getNamespace(NormalizedSource normalizedSource, KubernetesClientProperties kubernetesClientProperties) {
		if (!StringUtils.hasText(normalizedSource.getNamespace())) {
			return kubernetesClientProperties.getNamespace();
		}
		else {
			return normalizedSource.getNamespace();
		}
	}

	@Deprecated
	public static String getNamespace(ConfigMapConfigProperties.NormalizedSource normalizedSource,
			String fallbackNamespace) {
		String normalizedNamespace = normalizedSource.getNamespace();
		return StringUtils.hasText(normalizedNamespace) ? normalizedNamespace : fallbackNamespace;
	}

	@Deprecated
	public static String getNamespace(NormalizedSource normalizedSource, String fallbackNamespace) {
		String normalizedNamespace = normalizedSource.getNamespace();
		return StringUtils.hasText(normalizedNamespace) ? normalizedNamespace : fallbackNamespace;
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

}
