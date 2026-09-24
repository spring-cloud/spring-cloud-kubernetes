/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.reload;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import jakarta.annotation.PostConstruct;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.log.LogAccessor;

/**
 * Non-HA Secret change detector. Its informers are started during bean initialization.
 *
 * @author Ryan Baxter
 */
public class KubernetesClientEventBasedSecretsChangeDetector
		extends KubernetesClientEventBasedSecretsBaseChangeDetector {

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientEventBasedSecretsChangeDetector.class);

	public KubernetesClientEventBasedSecretsChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(strategy, propertySourceLocator, environment, coreV1Api, properties, kubernetesNamespaceProvider,
			KubernetesClientSecretsPropertySource.class);
	}

	@PostConstruct
	void inform() {
		LOG.info(() -> "config watcher HA is disabled : starting secret informers immediately");
		start();
	}

}
