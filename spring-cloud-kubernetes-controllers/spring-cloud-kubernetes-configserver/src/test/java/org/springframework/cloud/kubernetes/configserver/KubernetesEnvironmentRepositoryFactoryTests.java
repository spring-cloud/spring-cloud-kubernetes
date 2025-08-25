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

package org.springframework.cloud.kubernetes.configserver;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.kubernetes.configserver.configurations.ThirdConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ThirdConfig.class, properties = "test.third.config.enabled=true")
class KubernetesEnvironmentRepositoryFactoryTests {

	@Autowired
	private KubernetesEnvironmentRepository mockRepository;

	@Test
	void testBuild() {
		KubernetesEnvironmentRepositoryFactory factory = new KubernetesEnvironmentRepositoryFactory(mockRepository);
		KubernetesConfigServerProperties properties = new KubernetesConfigServerProperties();

		EnvironmentRepository repository = factory.build(properties);

		assertThat(repository).isNotNull();
		assertThat(repository).isInstanceOf(KubernetesEnvironmentRepository.class);
		assertThat(repository).isSameAs(mockRepository);
	}

}
