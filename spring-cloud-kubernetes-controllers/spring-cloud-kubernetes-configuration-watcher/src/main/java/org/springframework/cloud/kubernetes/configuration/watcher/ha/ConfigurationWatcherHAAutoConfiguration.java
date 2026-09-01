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

package org.springframework.cloud.kubernetes.configuration.watcher.ha;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoordinationV1Api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedConfigMapChangeDetector;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientEventBasedSecretsChangeDetector;
import org.springframework.cloud.kubernetes.client.leader.election.KubernetesClientLeaderElectionCallbacksAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.leader.election.ConditionalOnLeaderElectionEnabled;
import org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the components required for configuration watcher HA.
 *
 * <p>
 * Creates the persistent state store and the coordinator that starts and stops the
 * watchers when leadership changes.
 *
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnConfigurationWatcherHAEnabled
@ConditionalOnLeaderElectionEnabled
@ConditionalOnBean(ApiClient.class)
@AutoConfigureAfter(KubernetesClientAutoConfiguration.class)
@AutoConfigureBefore(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class)
class ConfigurationWatcherHAAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ConfigurationWatcherStateStore configurationWatcherStateStore(ApiClient apiClient,
			ConfigurationWatcherConfigurationProperties properties) {
		return new LeaseConfigurationWatcherStateStore(new CoordinationV1Api(apiClient), properties.getHa());
	}

	@Bean
	@ConditionalOnMissingBean
	ConfigurationWatcherHACoordinator configurationWatcherHACoordinator(
			ObjectProvider<KubernetesClientEventBasedConfigMapChangeDetector> configMapDetector,
			ObjectProvider<KubernetesClientEventBasedSecretsChangeDetector> secretsDetector,
			ConfigurationWatcherStateStore stateStore) {
		return new ConfigurationWatcherHACoordinator(configMapDetector, secretsDetector, stateStore);
	}

}
