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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.kubernetes.config.SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.config.reload.EventBasedSecretsChangeDetector;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author Ryan Baxter
 * @author Kris Iyer
 */
public abstract class SecretsWatcherChangeDetector extends EventBasedSecretsChangeDetector {

	protected Log log = LogFactory.getLog(getClass());

	private ScheduledExecutorService executorService;

	protected ConfigurationWatcherConfigurationProperties k8SConfigurationProperties;

	public SecretsWatcherChangeDetector(AbstractEnvironment environment, ConfigReloadProperties properties,
			KubernetesClient kubernetesClient, ConfigurationUpdateStrategy strategy,
			SecretsPropertySourceLocator secretsPropertySourceLocator,
			ConfigurationWatcherConfigurationProperties k8SConfigurationProperties,
			ThreadPoolTaskExecutor threadPoolTaskExecutor) {
		super(environment, properties, kubernetesClient, strategy, secretsPropertySourceLocator);
		this.executorService = Executors.newScheduledThreadPool(k8SConfigurationProperties.getThreadPoolSize(),
				threadPoolTaskExecutor);
		this.k8SConfigurationProperties = k8SConfigurationProperties;
	}

	protected boolean isSpringCloudKubernetesSecret(Secret secret) {
		if (secret.getMetadata() == null || secret.getMetadata().getLabels() == null) {
			return false;
		}
		return Boolean.parseBoolean(
				secret.getMetadata().getLabels().getOrDefault(k8SConfigurationProperties.getSecretLabel(), "false"));
	}

	protected abstract Mono<Void> triggerRefresh(Secret secret);

	@Override
	protected void onEvent(Secret secret) {
		if (isSpringCloudKubernetesSecret(secret)) {
			if (log.isDebugEnabled()) {
				log.debug("Scheduling remote refresh event to be published for Secret " + secret.getMetadata().getName()
						+ " to be published in " + k8SConfigurationProperties.getRefreshDelay().toMillis()
						+ " milliseconds");
			}
			executorService.schedule(() -> triggerRefresh(secret).subscribe(),
					k8SConfigurationProperties.getRefreshDelay().toMillis(), TimeUnit.MILLISECONDS);
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Not publishing event. Secret " + secret.getMetadata().getName()
						+ " does not contain the label " + k8SConfigurationProperties.getSecretLabel());
			}
		}
	}

}
