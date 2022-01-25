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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Baxter
 */
public class KubernetesConfigServerAutoConfigurationTests {

	@Configuration
	static class MockConfig {

		@Bean
		@Profile("kubernetesdisabled")
		public EnvironmentRepository environmentRepository() {
			return mock(EnvironmentRepository.class);
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = { "spring.profiles.include=kubernetes,kubernetesdisabled", "debug=true" },
			classes = { KubernetesConfigServerApplication.class, MockConfig.class })
	public class KubernetesDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			classes = { KubernetesConfigServerApplication.class, MockConfig.class },
			properties = { "spring.profiles.include=kubernetes,kubernetesdisabled", "debug=true" })
	@Nested
	class KubernetesProfileMissing {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.profiles.include=kubernetes", "debug=true",
					"spring.cloud.kubernetes.client.namespace=default" })
	@Nested
	class KubernetesEnabledProfileIncluded {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", // STOPSHIP: 11/10/21
					"spring.profiles.include=kubernetes", "debug=true",
					"spring.cloud.kubernetes.config.enabled=false" })
	@Nested
	class KubernetesEnabledProfileIncludedConfigMapDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.profiles.include=kubernetes", "debug=true",
					"spring.cloud.kubernetes.client.namespace=default" })
	@Nested
	class KubernetesEnabledProfileIncludedSecretsApiDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)[0])
					.isEqualTo("configMapPropertySourceSupplier");
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.profiles.include=kubernetes", "debug=true",
					"spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.kubernetes.secrets.enableApi=true" })
	@Nested
	class KubernetesEnabledProfileIncludedSecretsApiEnabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)).hasSize(2);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.profiles.include=kubernetes", "debug=true",
					"spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.kubernetes.config.enableApi=false" })
	@Nested
	class KubernetesEnabledProfileIncludedConfigApiDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		void runTest() {
			assertThat(context.getBeanNamesForType(KubernetesEnvironmentRepository.class)).hasSize(1);
			assertThat(context.getBeanNamesForType(KubernetesPropertySourceSupplier.class)).hasSize(0);
		}

	}

}
