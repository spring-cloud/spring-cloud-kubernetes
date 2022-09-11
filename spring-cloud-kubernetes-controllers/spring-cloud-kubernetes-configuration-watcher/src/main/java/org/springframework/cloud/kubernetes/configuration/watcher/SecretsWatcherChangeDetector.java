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

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.SECRET_APPS_DATA;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.SECRET_LABEL;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
abstract sealed class SecretsWatcherChangeDetector extends KubernetesClientEventBasedSecretsChangeDetector implements
		RefreshTrigger permits BusEventBasedSecretsWatcherChangeDetector,HttpBasedSecretsWatchChangeDetector {

	private final ScheduledExecutorService executorService;

	private final long refreshDelay;

	SecretsWatcherChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor) {
		super(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider);
		this.executorService = Executors.newScheduledThreadPool(k8SConfigurationProperties.getThreadPoolSize(),
				threadPoolTaskExecutor);
		this.refreshDelay = k8SConfigurationProperties.getRefreshDelay().toMillis();
	}

	@Override
	protected final void onEvent(KubernetesObject secret) {
		// this::refreshTrigger is coming from BusEventBasedSecretsWatcherChangeDetector
		WatcherUtil.onEvent(secret, SECRET_LABEL, SECRET_APPS_DATA, refreshDelay, executorService, "secret",
				this::triggerRefresh);
	}

}
