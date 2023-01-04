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

package org.springframework.cloud.kubernetes.client.config;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceDataEntriesProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.getApplicationNamespace;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientConfigMapPropertySourceLocator extends ConfigMapPropertySourceLocator {

	private static Log logger = LogFactory.getLog(ConfigMapPropertySourceLocator.class);

	private final CoreV1Api coreV1Api;

	private final KubernetesNamespaceProvider kubernetesNamespaceProvider;

	public KubernetesClientConfigMapPropertySourceLocator(CoreV1Api coreV1Api, ConfigMapConfigProperties properties,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(properties);
		this.coreV1Api = coreV1Api;
		this.kubernetesNamespaceProvider = kubernetesNamespaceProvider;
	}

	@Override
	protected MapPropertySource getMapPropertySource(NormalizedSource source, ConfigurableEnvironment environment) {

		String normalizedNamespace = source.namespace().orElse(null);
		String namespace = getApplicationNamespace(normalizedNamespace, source.target(), kubernetesNamespaceProvider);

		logger.debug("using namespace : " + namespace + " for source : " + source.name());

		KubernetesClientConfigContext context = new KubernetesClientConfigContext(coreV1Api, source, namespace,
				environment);
		return new KubernetesClientConfigMapPropertySource(context);
	}

	// called from config-data loader
	private static void logger(Log logger) {
		KubernetesClientConfigMapPropertySourceLocator.logger = logger;
	}

}
