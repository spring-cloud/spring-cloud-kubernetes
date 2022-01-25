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

import java.util.Arrays;
import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.KubernetesClientProperties.SERVICE_ACCOUNT_NAMESPACE_PATH;

/**
 * @author Ryan Baxter
 */
public abstract class AbstractKubernetesProfileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final DeferredLog LOG = new DeferredLog();

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

		application.addInitializers(ctx -> LOG.replayTo(AbstractKubernetesProfileEnvironmentPostProcessor.class));

		if (CloudPlatform.KUBERNETES.isActive(environment)) {
			addNamespaceFromServiceAccountFile(environment);
			addKubernetesProfileIfMissing(environment);
		}
	}

	protected abstract boolean isInsideKubernetes(Environment environment);

	private boolean hasKubernetesProfile(Environment environment) {
		return Arrays.stream(environment.getActiveProfiles()).anyMatch(KUBERNETES_PROFILE::equalsIgnoreCase);
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	private void addKubernetesProfileIfMissing(ConfigurableEnvironment environment) {
		if (isInsideKubernetes(environment)) {
			if (hasKubernetesProfile(environment)) {
				LOG.debug("'kubernetes' already in list of active profiles");
			}
			else {
				LOG.debug("Adding 'kubernetes' to list of active profiles");
				environment.addActiveProfile(KUBERNETES_PROFILE);
			}
		}
		else {
			LOG.warn("Not running inside kubernetes. Skipping 'kubernetes' profile activation.");
		}
	}

	private void addNamespaceFromServiceAccountFile(ConfigurableEnvironment environment) {
		String serviceAccountNamespacePathString = environment.getProperty(NAMESPACE_PATH_PROPERTY,
				SERVICE_ACCOUNT_NAMESPACE_PATH);
		String namespace = KubernetesNamespaceProvider
				.getNamespaceFromServiceAccountFile(serviceAccountNamespacePathString);
		if (StringUtils.hasText(namespace)) {
			environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
					Collections.singletonMap(NAMESPACE_PROPERTY, namespace)));
		}

	}

}
