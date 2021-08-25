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

package org.springframework.cloud.kubernetes.configserver;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @author Ryan Baxter
 */
@Configuration
@AutoConfigureAfter({ KubernetesClientAutoConfiguration.class })
@AutoConfigureBefore({ ConfigServerAutoConfiguration.class })
public class KubernetesConfigServerAutoConfiguration {

	@Bean
	@Profile("kubernetes")
	public EnvironmentRepository configMapEnvironmentRepository(CoreV1Api coreV1Api,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		return new ConfigMapEnvironmentRepository(coreV1Api, kubernetesNamespaceProvider.getNamespace());
	}

}
