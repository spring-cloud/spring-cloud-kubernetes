/*
 * Copyright 2019-2023 the original author or authors.
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

import java.util.List;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesClientInformerSelectiveNamespacesAutoConfigurationTests {

	private static final String NAMESPACE_A = "a";

	private static final String NAMESPACE_B = "b";

	private static final String NAMESPACE_C = "c";

	private static final String NAMESPACE_D = "d";

	@Test
	void testBeansCreates(CapturedOutput output) {

		new ApplicationContextRunner()
			.withPropertyValues("spring.cloud.discovery.enabled=true", "spring.cloud.kubernetes.discovery.enabled=true",
					"spring.cloud.kubernetes.discovery.namespaces[0]=" + NAMESPACE_A,
					"spring.cloud.kubernetes.discovery.namespaces[1]=" + NAMESPACE_B,
					"spring.main.cloud-platform=kubernetes")
			.withConfiguration(
					AutoConfigurations.of(KubernetesClientInformerSelectiveNamespacesAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				assertThat(context.getBean("selectiveNamespaces")).isNotNull();

				@SuppressWarnings("unchecked")
				Set<String> selectiveNamespaces = context.getBean("selectiveNamespaces", Set.class);
				Assertions.assertEquals(selectiveNamespaces, Set.of("a", "b"));

				@SuppressWarnings("unchecked")
				Set<String> namespaces = context.getBean("namespaces", Set.class);
				Assertions.assertEquals(namespaces, Set.of("c", "d"));
			});

		assertThat(output.getOut().contains("registering lister (for services) in namespace : " + NAMESPACE_A))
			.isTrue();
		assertThat(output.getOut().contains("registering lister (for services) in namespace : " + NAMESPACE_B))
			.isTrue();

		assertThat(output.getOut().contains("registering lister (for services) in namespace : " + NAMESPACE_C))
			.isFalse();
		assertThat(output.getOut().contains("registering lister (for services) in namespace : " + NAMESPACE_D))
			.isFalse();

		assertThat(output.getOut().contains("registering lister (for endpoints) in namespace : " + NAMESPACE_A))
			.isTrue();
		assertThat(output.getOut().contains("registering lister (for endpoints) in namespace : " + NAMESPACE_B))
			.isTrue();

		assertThat(output.getOut().contains("registering lister (for endpoints) in namespace : " + NAMESPACE_C))
			.isFalse();
		assertThat(output.getOut().contains("registering lister (for endpoints) in namespace : " + NAMESPACE_D))
			.isFalse();
	}

	@Configuration
	@EnableConfigurationProperties(KubernetesDiscoveryProperties.class)
	static class Config {

		@Bean
		ApiClient apiClient() {
			return Mockito.mock(ApiClient.class);
		}

		@Bean
		List<String> namespaces() {
			return List.of(NAMESPACE_C, NAMESPACE_D);
		}

	}

}
