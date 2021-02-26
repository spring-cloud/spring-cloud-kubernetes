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

package org.springframework.cloud.kubernetes.fabric8;

import java.nio.file.Paths;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.EnvReader;
import org.springframework.cloud.kubernetes.commons.LazilyInstantiate;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.util.StringUtils;

/**
 * Utility class to work with pods.
 *
 * @author Ioannis Canellos
 */
public class Fabric8PodUtils implements PodUtils<Pod> {

	/**
	 * HOSTNAME environment variable name.
	 */
	public static final String HOSTNAME = "HOSTNAME";

	/**
	 * KUBERNETES_SERVICE_HOST environment variable name.
	 */
	public static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";

	private static final Log LOG = LogFactory.getLog(Fabric8PodUtils.class);

	private final KubernetesClient client;

	private final String hostName;

	private final String serviceHost;

	private final Supplier<Pod> current;

	public Fabric8PodUtils(KubernetesClient client) {
		if (client == null) {
			throw new IllegalArgumentException("Must provide an instance of KubernetesClient");
		}

		this.client = client;
		this.hostName = EnvReader.getEnv(HOSTNAME);
		this.serviceHost = EnvReader.getEnv(KUBERNETES_SERVICE_HOST);
		this.current = LazilyInstantiate.using(this::internalGetPod);
	}

	@Override
	public Supplier<Pod> currentPod() {
		return this.current;
	}

	@Override
	public Boolean isInsideKubernetes() {
		return currentPod().get() != null;
	}

	private Pod internalGetPod() {
		try {
			if (isServiceHostEnvVarPresent() && isHostNameEnvVarPresent() && isServiceAccountFound()) {
				return this.client.pods().withName(this.hostName).get();
			}
		}
		catch (Throwable t) {
			LOG.warn("Failed to get pod with name:[" + this.hostName + "]. You should look into this if things aren't"
					+ " working as you expect. Are you missing serviceaccount permissions?", t);
		}
		return null;
	}

	private boolean isServiceHostEnvVarPresent() {
		return StringUtils.hasLength(serviceHost);
	}

	private boolean isHostNameEnvVarPresent() {
		return StringUtils.hasLength(hostName);
	}

	private boolean isServiceAccountFound() {
		boolean serviceAccountPathPresent = Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH).toFile().exists();
		if (!serviceAccountPathPresent) {
			// https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/
			LOG.warn("serviceaccount path not present, did you disable it via 'automountServiceAccountToken : false'?"
					+ " Major functionalities will not work without that property being set");
		}
		return serviceAccountPathPresent && Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH).toFile().exists();
	}

}
