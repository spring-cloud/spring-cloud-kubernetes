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

import java.nio.file.Paths;
import java.util.function.Supplier;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.LazilyInstantiate;
import org.springframework.cloud.kubernetes.commons.PodUtils;

/**
 * @author Ryan Baxter
 */
public class KubernetesClientPodUtils implements PodUtils<V1Pod> {

	/**
	 * Hostname environment variable name.
	 */
	public static final String HOSTNAME = "HOSTNAME";

	private static final Log LOG = LogFactory.getLog(KubernetesClientPodUtils.class);

	private final CoreV1Api client;

	private final String hostName;

	private Supplier<V1Pod> current;

	private String namespace;

	public KubernetesClientPodUtils(CoreV1Api client, String namespace) {
		if (client == null) {
			throw new IllegalArgumentException("Must provide an instance of KubernetesClient");
		}

		this.client = client;
		this.hostName = System.getenv(HOSTNAME);
		this.current = LazilyInstantiate.using(() -> internalGetPod());
		this.namespace = namespace;
	}

	@Override
	public Supplier<V1Pod> currentPod() {
		return this.current;
	}

	@Override
	public Boolean isInsideKubernetes() {
		return currentPod().get() != null;
	}

	private synchronized V1Pod internalGetPod() {
		try {
			LOG.info("Getting pod internal");
			if (isServiceAccountFound() && isHostNameEnvVarPresent()) {
				return this.client.readNamespacedPod(this.hostName, namespace, null, null, null);
			}
			else {
				return null;
			}
		}
		catch (Throwable t) {
			LOG.warn("Failed to get pod with name:[" + this.hostName + "]. You should look into this if things aren't"
					+ " working as you expect. Are you missing serviceaccount permissions?", t);
			return null;
		}
	}

	private boolean isHostNameEnvVarPresent() {
		return this.hostName != null && !this.hostName.isEmpty();
	}

	private boolean isServiceAccountFound() {
		return Paths.get(Config.SERVICEACCOUNT_TOKEN_PATH).toFile().exists()
				&& Paths.get(Config.SERVICEACCOUNT_CA_PATH).toFile().exists();
	}

}
