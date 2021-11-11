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

package org.springframework.cloud.kubernetes.fabric8.profile;

import org.junit.jupiter.api.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.kubernetes.example.App;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.profile.AbstractKubernetesProfileEnvironmentPostProcessor.KUBERNETES_PROFILE;

/**
 * @author Thomas Vitale
 */
class Fabric8ProfileEnvironmentPostProcessorTests {

	@Test
	void whenKubernetesEnvironmentAndNoApiAccessThenProfileEnabled() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
				.web(org.springframework.boot.WebApplicationType.NONE)
				.properties("KUBERNETES_SERVICE_HOST=10.0.0.1", "spring.main.cloud-platform=KUBERNETES").run();

		assertThat(context.getEnvironment().getActiveProfiles()).contains(KUBERNETES_PROFILE);
	}

	@Test
	void whenNoKubernetesEnvironmentAndNoApiAccessThenNoProfileEnabled() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
				.web(org.springframework.boot.WebApplicationType.NONE).run();

		assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain(KUBERNETES_PROFILE);
	}

}
