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

import java.util.List;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Service;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.DiscoveryClientConfigServiceBootstrapConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Zhanwei Wang
 * @author wind57
 */
class Fabric8DiscoveryClientConfigClientBootstrapConfigurationTests {

	@Test
	void onWhenRequested() {

		new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class,
				DiscoveryClientConfigServiceBootstrapConfiguration.class, ConfigClientProperties.class)
			.withPropertyValues(
				"spring.cloud.config.discovery.enabled=true",
				"spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.config.discovery.service-id=configserver")
			.run(context -> {
				DiscoveryClient discoveryClient = context.getBean(DiscoveryClient.class);
				ConfigClientProperties locator = context.getBean(ConfigClientProperties.class);
				verify(discoveryClient, Mockito.times(1)).getInstances("configserver");
				assertThat(locator.getUri()[0]).isEqualTo("http://fake:8888/");
			});

	}

	/**
	 * When using config-first bootstrap with Fabric8 Kubernetes discovery, the bootstrap context
	 * must include {@link Fabric8DiscoveryClientSpelAutoConfiguration} so that the
	 * required {@code Predicate<Service>} bean is available for
	 * {@link Fabric8DiscoveryClientAutoConfiguration}.
	 */
	@Test
	void spelPredicateBeanAvailableInBootstrapContext() {

		new ApplicationContextRunner()
			.withUserConfiguration(Fabric8DiscoveryClientConfigClientBootstrapConfiguration.class)
			.withPropertyValues(
				"spring.cloud.config.discovery.enabled=true",
				"spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.enabled=true")
			.run(context -> {
				ResolvableType servicesPredicate = ResolvableType.forClassWithGenerics(Predicate.class, Service.class);
				assertThat(context.getBeanNamesForType(servicesPredicate)).hasSize(1);
			});
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		Fabric8DiscoveryClient kubernetesDiscoveryClient() {
			Fabric8DiscoveryClient client = mock(Fabric8DiscoveryClient.class);
			ServiceInstance instance = new DefaultServiceInstance("configserver1", "configserver", "fake", 8888, false);
			when(client.getInstances("configserver")).thenReturn(List.of(instance));
			return client;
		}

	}

}
