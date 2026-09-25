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

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientAbstractConfigMapChangeDetector;
import reactor.core.publisher.Mono;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
public final class HttpBasedConfigMapWatchChangeDetector extends ConfigMapWatcherChangeDetector {

	private final HttpRefreshTrigger httpRefreshTrigger;

	public HttpBasedConfigMapWatchChangeDetector(HttpRefreshTrigger httpRefreshTrigger,
			KubernetesClientAbstractConfigMapChangeDetector baseChangeDetector,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor) {
		super(baseChangeDetector, k8SConfigurationProperties, threadPoolTaskExecutor);
		this.httpRefreshTrigger = httpRefreshTrigger;
	}

	@Override
	public Mono<Void> triggerRefresh(KubernetesSource kubernetesSource) {
		return httpRefreshTrigger.triggerRefresh(kubernetesSource);
	}

}
