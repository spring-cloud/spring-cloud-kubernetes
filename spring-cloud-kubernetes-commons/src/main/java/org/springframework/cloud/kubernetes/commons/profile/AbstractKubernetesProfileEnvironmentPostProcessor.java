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

package org.springframework.cloud.kubernetes.commons.profile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.commons.KubernetesClientProperties.SERVICE_ACCOUNT_NAMESPACE_PATH;

/**
 * @author Ryan Baxter
 */
public abstract class AbstractKubernetesProfileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final Log LOG = LogFactory.getLog(AbstractKubernetesProfileEnvironmentPostProcessor.class);

	private static final String NAMESPACE_PATH_PROPERTY = "spring.cloud.kubernetes.client.serviceAccountNamespacePath";

	protected static final String NAMESPACE_PROPERTY = "spring.cloud.kubernetes.client.namespace";

	private static final String PROPERTY_SOURCE_NAME = "KUBERNETES_NAMESPACE_PROPERTY_SOURCE";

	// Before ConfigFileApplicationListener so values there can use these ones
	private static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER - 1;

	/**
	 * Profile name.
	 */
	public static final String KUBERNETES_PROFILE = "kubernetes";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

		final boolean kubernetesEnabled = environment.getProperty("spring.cloud.kubernetes.enabled", Boolean.class,
				true);
		if (!kubernetesEnabled) {
			return;
		}
		addNamespaceFromServiceAccountFile(environment);
		if (isInsideKubernetes(environment)) {
			if (hasKubernetesProfile(environment)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("'kubernetes' already in list of active profiles");
				}
			}
			else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Adding 'kubernetes' to list of active profiles");
				}
				environment.addActiveProfile(KUBERNETES_PROFILE);
			}
		}
		else {
			if (LOG.isDebugEnabled()) {
				LOG.warn("Not running inside kubernetes. Skipping 'kubernetes' profile activation.");
			}
		}
	}

	protected abstract boolean isInsideKubernetes(Environment environment);

	private boolean hasKubernetesProfile(Environment environment) {
		for (String activeProfile : environment.getActiveProfiles()) {
			if (KUBERNETES_PROFILE.equalsIgnoreCase(activeProfile)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	private void addNamespaceFromServiceAccountFile(ConfigurableEnvironment environment) {
		Map<String, Object> properties = new HashMap<>();

		String serviceAccountNamespace = environment.getProperty(NAMESPACE_PATH_PROPERTY,
				SERVICE_ACCOUNT_NAMESPACE_PATH);
		LOG.debug("Looking for service account namespace at " + serviceAccountNamespace);
		boolean serviceAccountNamespaceExists = Files.isRegularFile((new File(serviceAccountNamespace)).toPath(),
				new LinkOption[0]);
		if (serviceAccountNamespaceExists) {
			LOG.debug("Found service account namespace at: [" + serviceAccountNamespace + "].");

			try {
				String namespace = new String(Files.readAllBytes((new File(serviceAccountNamespace)).toPath()));
				LOG.debug("Service account namespace value: " + namespace);
				properties.put(NAMESPACE_PROPERTY, namespace);
			}
			catch (IOException var4) {
				LOG.error("Error reading service account namespace from: [" + serviceAccountNamespace + "].", var4);
			}
		}
		else {
			LOG.info("Did not find service account namespace at: [" + serviceAccountNamespace + "]. Ignoring.");
		}
		environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
	}

}
