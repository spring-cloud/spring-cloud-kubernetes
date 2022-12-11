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

package org.springframework.cloud.kubernetes.client;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.NamespaceResolutionFailedException;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientUtils {

	private static final Log LOG = LogFactory.getLog(KubernetesClientUtils.class);

	private KubernetesClientUtils() {
	}

	public static ApiClient createApiClientForInformerClient() {
		ApiClient apiClient = kubernetesApiClient();
		apiClient.setReadTimeout(0);
		return apiClient;
	}

	public static ApiClient kubernetesApiClient() {
		try {
			// Assume we are running in a cluster
			ApiClient apiClient = ClientBuilder.cluster().build();
			LOG.info("Created API client in the cluster.");
			return apiClient;
		}
		catch (Exception e) {
			LOG.info("Could not create the Kubernetes ApiClient in a cluster environment, because : ", e);
			LOG.info("Trying to use a \"standard\" configuration to create the Kubernetes ApiClient");
			try {
				ApiClient apiClient = ClientBuilder.defaultClient();
				LOG.info("Created standard API client. Unless $KUBECONFIG or $HOME/.kube/config is defined, "
						+ "this client will try to connect to localhost:8080");
				return apiClient;
			}
			catch (Exception e1) {
				LOG.warn("Could not create a Kubernetes ApiClient from either a cluster or standard environment. "
						+ "Will return one that always connects to localhost:8080", e1);
				return new ClientBuilder().build();
			}
		}
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
	public static String getApplicationNamespace(String namespace, String configurationTarget,
			KubernetesNamespaceProvider provider) {
		if (StringUtils.hasText(namespace)) {
			LOG.debug(configurationTarget + " namespace : " + namespace);
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
