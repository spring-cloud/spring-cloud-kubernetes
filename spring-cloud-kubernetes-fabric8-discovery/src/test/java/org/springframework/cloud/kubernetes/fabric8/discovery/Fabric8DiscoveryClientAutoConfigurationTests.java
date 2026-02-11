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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.use-endpoint-slices=false" })
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryClientAutoConfigurationTests {

	private static KubernetesClient kubernetesClient;

	@Autowired
	private DiscoveryClient discoveryClient;

	@Test
	void kubernetesDiscoveryClientCreated() {
		assertThat(this.discoveryClient).isInstanceOf(CompositeDiscoveryClient.class);

		CompositeDiscoveryClient composite = (CompositeDiscoveryClient) this.discoveryClient;
		assertThat(composite.getDiscoveryClients().stream().anyMatch(dc -> dc instanceof Fabric8DiscoveryClient))
			.isTrue();
	}

	@SpringBootApplication
	protected static class TestConfig {

		@Bean
		KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider provider = mock(KubernetesNamespaceProvider.class);
			when(provider.getNamespace()).thenReturn("test");
			return provider;
		}

		@Bean
		KubernetesClient kubernetesClient() {
			return kubernetesClient;
		}

	}

}
