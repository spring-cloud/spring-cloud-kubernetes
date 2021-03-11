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

package org.springframework.cloud.kubernetes.client.discovery.reactive;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.cloud.kubernetes.discovery.cacheLoadingTimeoutSeconds=5",
				"spring.cloud.config.enabled=false", "spring.cloud.kubernetes.discovery.waitCacheReady=false",
				"spring.main.web-application-type=reactive" })
public class KubernetesInformerReactiveDiscoveryClientAutoConfigurationTests {

	@Autowired(required = false)
	private ReactiveDiscoveryClient discoveryClient;

	@Test
	public void kubernetesDiscoveryClientCreated() {
		assertThat(this.discoveryClient).isNotNull().isInstanceOf(ReactiveCompositeDiscoveryClient.class);

		ReactiveCompositeDiscoveryClient composite = (ReactiveCompositeDiscoveryClient) this.discoveryClient;
		assertThat(composite.getDiscoveryClients().stream()
				.anyMatch(dc -> dc instanceof KubernetesInformerReactiveDiscoveryClient)).isTrue();
	}

	@SpringBootApplication
	protected static class TestConfig {

		@Bean
		public ApiClient apiClient() {
			ApiClient apiClient = mock(ApiClient.class);
			when(apiClient.getJSON()).thenReturn(new JSON());
			when(apiClient.getHttpClient()).thenReturn(new OkHttpClient.Builder().build());
			return apiClient;
		}

	}

}
