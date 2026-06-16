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

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
abstract sealed class SecretsWatcherChangeDetector extends KubernetesClientEventBasedSecretsChangeDetector implements
		RefreshTrigger permits BusEventBasedSecretsWatcherChangeDetector, HttpBasedSecretsWatchChangeDetector {

	private final ScheduledExecutorService executorService;

	/**
	 * <pre>
	 * Read refreshDelay from the properties bean when handling an event instead of
	 * copying it once in the constructor. The configuration watcher can be configured
	 * from a ConfigMap and can refresh itself, so changes in
	 * ConfigurationWatcherConfigurationProperties must be visible here after that
	 * refresh.
	 *
	 * RefreshScope annotation is not an option here:
	 * - class-based refresh proxies require subclassing, but HttpRefreshTrigger is final
	 * - interface-based refresh proxies would have to implement RefreshTrigger, but
	 *   RefreshTrigger is sealed
	 * For these reasons, we always read the current values from the properties bean.
	 * </pre>
	 */
	private final ConfigurationWatcherConfigurationProperties k8SConfigurationProperties;

	SecretsWatcherChangeDetector(CoreV1Api coreV1Api, ConfigurableEnvironment environment,
			ConfigReloadProperties properties, ConfigurationUpdateStrategy strategy,
			KubernetesClientSecretsPropertySourceLocator propertySourceLocator,
			KubernetesNamespaceProvider kubernetesNamespaceProvider,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor) {
		super(coreV1Api, environment, properties, strategy, propertySourceLocator, kubernetesNamespaceProvider);
		this.executorService = Executors.newScheduledThreadPool(k8SConfigurationProperties.getThreadPoolSize(),
				threadPoolTaskExecutor);
		this.k8SConfigurationProperties = k8SConfigurationProperties;
	}

	@Override
	protected final void onEvent(KubernetesObject secret) {
		WatcherUtil.onEvent(secret, k8SConfigurationProperties.getRefreshDelay().toMillis(), executorService,
				this::triggerRefresh);
	}

}
