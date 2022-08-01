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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
public abstract class ConfigMapWatcherChangeDetector extends KubernetesClientEventBasedConfigMapChangeDetector {

	protected Log log = LogFactory.getLog(getClass());

	private final ScheduledExecutorService executorService;

	protected ConfigurationWatcherConfigurationProperties k8SConfigurationProperties;

	public ConfigMapWatcherChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientConfigMapPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor) {
		super(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider);

		this.executorService = Executors.newScheduledThreadPool(k8SConfigurationProperties.getThreadPoolSize(),
				threadPoolTaskExecutor);
		this.k8SConfigurationProperties = k8SConfigurationProperties;
	}

	@Override
	protected void onEvent(V1ConfigMap configMap) {
		if (isSpringCloudKubernetesConfig(configMap)) {
			if (log.isDebugEnabled()) {
				log.debug("Scheduling remote refresh event to be published for ConfigMap "
						+ configMap.getMetadata().getName() + " to be published in "
						+ k8SConfigurationProperties.getRefreshDelay().toMillis() + " milliseconds");
			}
			executorService.schedule(() -> triggerRefresh(configMap).subscribe(),
					k8SConfigurationProperties.getRefreshDelay().toMillis(), TimeUnit.MILLISECONDS);
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Not publishing event. ConfigMap " + configMap.getMetadata().getName()
						+ " does not contain the label " + k8SConfigurationProperties.getConfigLabel());
			}
		}
	}

	protected boolean isSpringCloudKubernetesConfig(V1ConfigMap configMap) {
		if (configMap.getMetadata() == null || configMap.getMetadata().getLabels() == null) {
			return false;
		}
		return Boolean.parseBoolean(
				configMap.getMetadata().getLabels().getOrDefault(k8SConfigurationProperties.getConfigLabel(), "false"));
	}

	protected abstract Mono<Void> triggerRefresh(V1ConfigMap configMap);

}
