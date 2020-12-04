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

import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Ryan Baxter
 */
public final class KubernetesClientUtils {

	private static final Log LOG = LogFactory.getLog(KubernetesClientUtils.class);

	private KubernetesClientUtils() {
	}

	public static ApiClient kubernetesApiClient() throws IOException {
		try {
			String host = System.getenv("KUBERNETES_SERVICE_HOST");
			String port = System.getenv("KUBERNETES_SERVICE_PORT");
			// Assume we are running in a cluster
			ApiClient apiClient = null;
			if (host != null && port != null) {
				apiClient = ClientBuilder.cluster().build();
				LOG.info("Created API client in the cluster.");
			} else {
				apiClient = ClientBuilder.standard().build();
				LOG.info("Created API client using \"standard\" configuration.");
			}
			return apiClient;
		}
		catch (Exception e) {
			LOG.info(
					"Could not create the Kubernetes ApiClient in a cluster environment, trying to use a \"standard\" configuration instead.",
					e);
			try {
				ApiClient apiClient = ClientBuilder.standard().build();
				return apiClient;
			}
			catch (IOException e1) {
				LOG.warn("Could not create a Kubernetes ApiClient from either a cluster or standard environment", e1);
				throw e1;
			}
		}
	}

}
