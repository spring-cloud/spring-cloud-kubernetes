/*
 * Copyright 2013-2022 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@SpringBootTest
class KubernetesEnvironmentRepositoryFactoryTests {

	@MockBean
	private ObjectProvider<KubernetesEnvironmentRepository> kubernetesEnvironmentRepositoryProvider;

	@Test
	void testBuild() {
		KubernetesEnvironmentRepository mockRepository = mock(KubernetesEnvironmentRepository.class);
		when(kubernetesEnvironmentRepositoryProvider.getIfAvailable()).thenReturn(mockRepository);

		KubernetesEnvironmentRepositoryFactory factory = new KubernetesEnvironmentRepositoryFactory(
				kubernetesEnvironmentRepositoryProvider);
		KubernetesConfigServerProperties properties = new KubernetesConfigServerProperties();

		EnvironmentRepository repository = factory.build(properties);

		assertThat(repository).isNotNull();
		assertThat(repository).isInstanceOf(KubernetesEnvironmentRepository.class);
		assertThat(repository).isEqualTo(mockRepository);
	}

	@Configuration
	static class TestConfig {

		@Bean
		public KubernetesEnvironmentRepositoryFactory kubernetesEnvironmentRepositoryFactory(
				ObjectProvider<KubernetesEnvironmentRepository> kubernetesEnvironmentRepositoryProvider) {
			return new KubernetesEnvironmentRepositoryFactory(kubernetesEnvironmentRepositoryProvider);
		}

	}

}
