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

package org.springframework.cloud.kubernetes.client.discovery;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1EndpointsListBuilder;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.context.annotation.Bean;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.cacheLoadingTimeoutSeconds=5", "spring.cloud.config.enabled=false",
				"spring.cloud.kubernetes.discovery.waitCacheReady=false" })
class KubernetesDiscoveryClientAutoConfigurationTests {

	@Autowired
	private DiscoveryClient discoveryClient;

	private static WireMockServer wireMockServer;

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@Test
	void kubernetesDiscoveryClientCreated() {
		assertThat(this.discoveryClient).isNotNull().isInstanceOf(CompositeDiscoveryClient.class);

		CompositeDiscoveryClient composite = (CompositeDiscoveryClient) this.discoveryClient;
		assertThat(composite.getDiscoveryClients().stream()
				.anyMatch(dc -> dc instanceof KubernetesInformerDiscoveryClient)).isTrue();
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
		ApiClient apiClient() {
			wireMockServer = new WireMockServer(options().dynamicPort());
			wireMockServer.start();
			WireMock.configureFor(wireMockServer.port());
			stubFor(get("/api/v1/namespaces/test/endpoints?resourceVersion=0&watch=false")
					.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1EndpointsListBuilder()
							.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build()).build()))));
			stubFor(get("/api/v1/namespaces/test/services?resourceVersion=0&watch=false")
					.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1ServiceListBuilder()
							.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build()).build()))));
			return new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
		}

	}

}
