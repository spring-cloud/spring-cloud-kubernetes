/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8;

import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.annotation.Nullable;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.StringUtils;

/**
 * Utility class related to Fabric8 Client. It resides in this module because it is
 * supposed to be re-used in fabric8 specific ones (like config or discovery)
 *
 * @author wind57
 */
public final class Fabric8Utils {

	private Fabric8Utils() {

	}

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8Utils.class));

	/**
	 * this method does the namespace resolution. Namespace is being searched according to
	 * the order below.
	 *
	 * <pre>
	 *     1. from incoming namespace, which can be null.
	 *     2. from a property 'spring.cloud.kubernetes.client.namespace', if such is present.
	 *     3. from a String residing in a file denoted by `spring.cloud.kubernetes.client.serviceAccountNamespacePath`
	 * 	      property, if such is present.
	 * 	   4. from a String residing in `/var/run/secrets/kubernetes.io/serviceaccount/namespace` file,
	 * 	  	  if such is present (kubernetes default path)
	 * 	   5. from KubernetesClient::getNamespace, which is implementation specific.
	 * </pre>
	 *
	 * If any of the above fail, we throw a {@link NamespaceResolutionFailedException}.
	 * @param namespace normalized namespace
	 * @param configurationTarget Config Map/Secret
	 * @param provider the provider which computes the namespace
	 * @param client fabric8 Kubernetes client
	 * @return application namespace
	 * @throws NamespaceResolutionFailedException when namespace could not be resolved
	 */
	public static String getApplicationNamespace(KubernetesClient client, @Nullable String namespace,
			String configurationTarget, KubernetesNamespaceProvider provider) {

		if (StringUtils.hasText(namespace)) {
			LOG.debug(configurationTarget + " namespace : " + namespace);
			return namespace;
		}

		if (provider != null) {
			String providerNamespace = provider.getNamespace();
			if (StringUtils.hasText(providerNamespace)) {
				LOG.debug(() -> configurationTarget + " namespace from provider : " + providerNamespace);
				return providerNamespace;
			}
		}

		String clientNamespace = client.getNamespace();
		LOG.debug(() -> configurationTarget + " namespace from client : " + clientNamespace);
		if (clientNamespace == null) {
			throw new NamespaceResolutionFailedException("unresolved namespace");
		}
		return clientNamespace;

	}

}
