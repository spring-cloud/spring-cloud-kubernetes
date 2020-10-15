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

package org.springframework.cloud.kubernetes.client;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Ryan Baxter
 */
@Configuration
@ConditionalOnKubernetesEnabled
@AutoConfigureAfter(KubernetesCommonsAutoConfiguration.class)
public class KubernetesClientAutoConfiguration {

	private static final Log LOG = LogFactory.getLog(KubernetesClientAutoConfiguration.class);

	private ApiClient kubernetesApiClient() throws IOException {
		try {
			// Assume we are running in a cluster
			ApiClient apiClient = ClientBuilder.cluster().build();
			io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
			LOG.info("Created API client in the cluster.");
			return apiClient;
		}
		catch (Exception e) {
			LOG.info(
					"Could not create the Kubernetes ApiClient in a cluster environment, trying to use a \"standard\" configuration instead.",
					e);
			try {
				ApiClient apiClient = ClientBuilder.standard().build();
				io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
				return apiClient;
			}
			catch (IOException e1) {
				LOG.warn("Could not create a Kubernetes ApiClient from either a cluster or standard environment", e1);
				throw e1;
			}
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public CoreV1Api coreApi() throws IOException {
		kubernetesApiClient();
		return new CoreV1Api();
	}

	@Bean
	@ConditionalOnMissingBean
	public KubernetesClientPodUtils kubernetesPodUtils(CoreV1Api client,
			KubernetesClientProperties kubernetesClientProperties) {
		return new KubernetesClientPodUtils(client, kubernetesClientProperties.getNamespace());
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	protected static class KubernetesActuatorConfiguration {

		@Bean
		@ConditionalOnEnabledHealthIndicator("kubernetes")
		public KubernetesClientHealthIndicator kubernetesHealthIndicator(PodUtils podUtils) {
			return new KubernetesClientHealthIndicator(podUtils);
		}

		@Bean
		@ConditionalOnEnabledInfoContributor("kubernetes")
		public KubernetesClientInfoContributor kubernetesInfoContributor(PodUtils podUtils) {
			return new KubernetesClientInfoContributor(podUtils);
		}

	}

}
