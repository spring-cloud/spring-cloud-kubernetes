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

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.kubernetes.commons.ConditionalOnKubernetesEnabled;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.client.KubernetesClientUtils.kubernetesApiClient;

/**
 * @author Ryan Baxter
 */
@Configuration
@ConditionalOnKubernetesEnabled
@AutoConfigureAfter(KubernetesCommonsAutoConfiguration.class)
public class KubernetesClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ApiClient apiClient() {
		ApiClient apiClient = kubernetesApiClient();
		io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
		return apiClient;
	}

	@Bean
	@ConditionalOnMissingBean
	public CoreV1Api coreApi(ApiClient apiClient) {
		return new CoreV1Api(apiClient);
	}

	@Bean
	@ConditionalOnMissingBean
	public KubernetesNamespaceProvider kubernetesNamespaceProvider(Environment environment) {
		return new KubernetesNamespaceProvider(environment);
	}

	@Bean
	@ConditionalOnMissingBean
	public KubernetesClientPodUtils kubernetesPodUtils(CoreV1Api client,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new KubernetesClientPodUtils(client, kubernetesNamespaceProvider.getNamespace());
	}

}
