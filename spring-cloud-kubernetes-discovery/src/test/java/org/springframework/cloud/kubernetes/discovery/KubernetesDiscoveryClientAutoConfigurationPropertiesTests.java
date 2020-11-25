/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Dawson
 * @author Tim Ysewyn
 */
public class KubernetesDiscoveryClientAutoConfigurationPropertiesTests {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void kubernetesDiscoveryDisabled() throws Exception {
		setup("spring.cloud.kubernetes.discovery.enabled=false",
				"spring.cloud.kubernetes.discovery.catalog-services-watch.enabled=false");
		assertThat(this.context.getBeanNamesForType(KubernetesDiscoveryClient.class)).isEmpty();
	}

	@Test
	public void kubernetesDiscoveryWhenKubernetesDisabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=false");
		assertThat(this.context.getBeanNamesForType(KubernetesDiscoveryClient.class)).isEmpty();
	}

	@Test
	public void kubernetesDiscoveryWhenDiscoveryDisabled() throws Exception {
		setup("spring.cloud.discovery.enabled=false");
		assertThat(this.context.getBeanNamesForType(KubernetesDiscoveryClient.class)).isEmpty();
	}

	@Test
	public void kubernetesDiscoveryDefaultEnabled() throws Exception {
		setup("spring.cloud.kubernetes.enabled=true");
		assertThat(this.context.getBeanNamesForType(KubernetesDiscoveryClient.class)).hasSize(1);
	}

	private void setup(String... env) {
		this.context = new SpringApplicationBuilder(PropertyPlaceholderAutoConfiguration.class,
				KubernetesClientTestConfiguration.class, KubernetesDiscoveryClientAutoConfiguration.class)
						.web(org.springframework.boot.WebApplicationType.NONE).properties(env).run();
	}

	@Configuration(proxyBeanMethods = false)
	static class KubernetesClientTestConfiguration {

		@Bean
		KubernetesClient kubernetesClient() {
			return mock(KubernetesClient.class);
		}

	}

}
