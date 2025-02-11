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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = { KubernetesConfigServerApplication.class },
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.profiles.include=kubernetes",
				"spring.cloud.kubernetes.client.namespace=default", "spring.profiles.active=composite",
				"spring.cloud.config.server.composite[0].type=git",
				"spring.cloud.config.server.composite[0].uri=https://github.com/spring-cloud-samples/config-repo",
				"spring.cloud.config.server.composite[1].type=kubernetes",
				"spring.cloud.config.server.composite[1].config-map-namespace=default",
				"spring.cloud.config.server.composite[1].secrets-namespace=default" })
class CompositeProfileWithGitAndKubernetesConfigSourcesTests {

	@Autowired
	private ConfigurableApplicationContext context;

	@Test
	void kubernetesEnvironmentRepositoryIsLoaded() {
		assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(2);
	}

	@Test
	void kubernetesPropertySourceSuppliersAreLoaded() {
		assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)).isNotEmpty();
	}

	@Test
	void gitEnvironmentRepositoryIsLoaded() {
		assertThat(context.getBeanNamesForType(JGitEnvironmentRepository.class)).hasSize(1);
	}

}
