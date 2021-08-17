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

package org.springframework.cloud.kubernetes.commons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.commons.KubernetesClientProperties.SERVICE_ACCOUNT_NAMESPACE_PATH;

/**
 * @author Ryan Baxter
 */
public class KubernetesNamespaceProvider {

	private static final DeferredLog LOG = new DeferredLog();

	/**
	 * Property name for namespace.
	 */
	public static final String NAMESPACE_PROPERTY = "spring.cloud.kubernetes.client.namespace";

	/**
	 * Property for namespace file path.
	 */
	public static final String NAMESPACE_PATH_PROPERTY = "spring.cloud.kubernetes.client.serviceAccountNamespacePath";

	private String serviceAccountNamespace;

	private final Environment environment;

	public KubernetesNamespaceProvider(Environment env) {
		this.environment = env;
		LOG.replayTo(KubernetesNamespaceProvider.class);
	}

	public String getNamespace() {
		String namespace = environment.getProperty(NAMESPACE_PROPERTY);
		return namespace != null ? namespace : getServiceAccountNamespace();
	}

	private String getServiceAccountNamespace() {
		String serviceAccountNamespacePathString = environment.getProperty(NAMESPACE_PATH_PROPERTY,
				SERVICE_ACCOUNT_NAMESPACE_PATH);
		if (serviceAccountNamespace == null) {
			serviceAccountNamespace = getNamespaceFromServiceAccountFile(serviceAccountNamespacePathString);
		}
		return serviceAccountNamespace;
	}

	public static String getNamespaceFromServiceAccountFile(String path) {
		String namespace = null;
		LOG.debug("Looking for service account namespace at: [" + path + "].");
		Path serviceAccountNamespacePath = Paths.get(path);
		boolean serviceAccountNamespaceExists = Files.isRegularFile(serviceAccountNamespacePath);
		if (serviceAccountNamespaceExists) {
			LOG.debug("Found service account namespace at: [" + serviceAccountNamespacePath + "].");

			try {
				namespace = new String(Files.readAllBytes((serviceAccountNamespacePath)));
				LOG.debug("Service account namespace value: " + serviceAccountNamespacePath);
			}
			catch (IOException ioe) {
				LOG.error("Error reading service account namespace from: [" + serviceAccountNamespacePath + "].", ioe);
			}

		}
		return namespace;
	}

}
