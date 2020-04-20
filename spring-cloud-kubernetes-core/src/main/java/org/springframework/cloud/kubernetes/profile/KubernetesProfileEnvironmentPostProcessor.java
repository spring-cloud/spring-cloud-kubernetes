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

package org.springframework.cloud.kubernetes.profile;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.kubernetes.StandardPodUtils;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

public class KubernetesProfileEnvironmentPostProcessor
		implements EnvironmentPostProcessor, Ordered {

	private static final Log LOG = LogFactory
			.getLog(KubernetesProfileEnvironmentPostProcessor.class);

	// Before ConfigFileApplicationListener so values there can use these ones
	private static final int ORDER = ConfigFileApplicationListener.DEFAULT_ORDER - 1;

	private static final String KUBERNETES_PROFILE = "kubernetes";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment,
			SpringApplication application) {

		final boolean kubernetesEnabled = environment
				.getProperty("spring.cloud.kubernetes.enabled", Boolean.class, true);
		if (!kubernetesEnabled) {
			return;
		}

		if (isInsideKubernetes()) {
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
				LOG.warn(
						"Not running inside kubernetes. Skipping 'kubernetes' profile activation.");
			}
		}
	}

	private boolean isInsideKubernetes() {
		try (DefaultKubernetesClient client = new DefaultKubernetesClient()) {
			final StandardPodUtils podUtils = new StandardPodUtils(client);
			return podUtils.isInsideKubernetes();
		}
	}

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

}
