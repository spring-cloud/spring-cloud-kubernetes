/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver.configurations;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.configserver.KubernetesEnvironmentRepository;
import org.springframework.cloud.kubernetes.configserver.KubernetesEnvironmentRepositoryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
@ConditionalOnProperty(value = "test.third.config.enabled", havingValue = "true", matchIfMissing = false)
public class ThirdConfig {

	@Bean
	public KubernetesEnvironmentRepository kubernetesEnvironmentRepository() {
		return mock(KubernetesEnvironmentRepository.class);
	}

	@Bean
	public KubernetesEnvironmentRepositoryFactory kubernetesEnvironmentRepositoryFactory(
			KubernetesEnvironmentRepository kubernetesEnvironmentRepository) {
		return new KubernetesEnvironmentRepositoryFactory(kubernetesEnvironmentRepository);
	}

}
