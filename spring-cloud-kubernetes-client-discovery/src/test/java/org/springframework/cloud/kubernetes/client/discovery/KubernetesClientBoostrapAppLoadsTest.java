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

package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * proves that
 * <a href="https://github.com/spring-cloud/spring-cloud-kubernetes/issues/2250">2250</a>
 * is fixed
 *
 * @author wind57
 */
class KubernetesClientBoostrapAppLoadsTest {

	@Test
	void loadsBootstrapConfigurationFromMainSpringFactoriesAtRuntime() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
			.web(WebApplicationType.NONE)
			.properties("spring.cloud.bootstrap.enabled=true", "spring.main.cloud-platform=KUBERNETES",
					"spring.cloud.config.discovery.enabled=true", "spring.cloud.discovery.enabled=true",
					"spring.cloud.config.import-check.enabled=false",
					"spring.main.allow-bean-definition-overriding=true")
			// "spring.cloud.bootstrap.sources=" +
			// KubernetesCommonsAutoConfiguration.class.getName() + ","
			// + BootstrapTestConfiguration.class.getName())
			.run()) {

			ConfigurableApplicationContext bootstrap = (ConfigurableApplicationContext) context.getParent();

			assertThat(bootstrap).isNotNull();
			assertThat(bootstrap.getBean(KubernetesClientDiscoveryClientConfigClientBootstrapConfiguration.class))
				.isNotNull();

			// Stronger proof: this bean exists only because the bootstrap configuration
			// imported KubernetesClientAutoConfiguration at runtime.
			assertThat(bootstrap.getBean(CoreV1Api.class)).isNotNull();
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class TestApplication {

	}

	@Configuration(proxyBeanMethods = false)
	static class BootstrapTestConfiguration {

		@Bean
		ApiClient apiClient() {
			return new ApiClient();
		}

		@Bean
		KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			return new KubernetesNamespaceProvider("test");
		}

	}

}
