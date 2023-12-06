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

package org.springframework.cloud.kubernetes.discoveryserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsListBuilder;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = DiscoveryServerIntegrationTests.TestConfig.class)
class DiscoveryServerIntegrationTests {

	private static final V1Service TEST_SERVICE_1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints TEST_ENDPOINTS_1 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080).name("http"))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")
							.targetRef(new V1ObjectReferenceBuilder().withUid("uid1").build())));

	private static final V1Service TEST_SERVICE_2 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service TEST_SERVICE_3 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1").putLabelsItem("spring", "true")
					.putLabelsItem("k8s", "true"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints TEST_ENDPOINTS_3 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080).name("http"))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")
							.targetRef(new V1ObjectReferenceBuilder().withUid("uid2").build())));

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void apps() {
		Map<String, String> kubernetesServiceInstance1Metadata = new HashMap<>();
		kubernetesServiceInstance1Metadata.put(TEST_ENDPOINTS_1.getSubsets().get(0).getPorts().get(0).getName(),
				TEST_ENDPOINTS_1.getSubsets().get(0).getPorts().get(0).getPort().toString());

		Map<String, String> kubernetesServiceInstance2Metadata = new HashMap<>();
		kubernetesServiceInstance2Metadata.put(TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getName(),
				TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(TEST_SERVICE_3.getMetadata().getLabels());

		KubernetesServiceInstance kubernetesServiceInstance1 = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS_1.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE_1.getMetadata().getName(), TEST_ENDPOINTS_1.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS_1.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance1Metadata,
				false, TEST_SERVICE_1.getMetadata().getNamespace(), null);
		KubernetesServiceInstance kubernetesServiceInstance3 = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS_3.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE_3.getMetadata().getName(), TEST_ENDPOINTS_3.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata,
				false, TEST_SERVICE_3.getMetadata().getNamespace(), null);

		webTestClient.get().uri("/apps").exchange().expectBodyList(DiscoveryServerController.Service.class).hasSize(2).contains(
				new DiscoveryServerController.Service(TEST_SERVICE_1.getMetadata().getName(),
						Collections.singletonList(kubernetesServiceInstance1)),
				new DiscoveryServerController.Service(TEST_SERVICE_3.getMetadata().getName(),
						Collections.singletonList(kubernetesServiceInstance3)));
	}

	@Test
	void appsName() {
		Map<String, String> kubernetesServiceInstance2Metadata = new HashMap<>();
		kubernetesServiceInstance2Metadata.put(TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getName(),
				TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(TEST_SERVICE_3.getMetadata().getLabels());
		KubernetesServiceInstance kubernetesServiceInstance3 = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS_3.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE_3.getMetadata().getName(), TEST_ENDPOINTS_3.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata,
				false, TEST_SERVICE_3.getMetadata().getNamespace(), null);
		webTestClient.get().uri("/apps/test-svc-3").exchange().expectBodyList(KubernetesServiceInstance.class)
				.hasSize(1).contains(kubernetesServiceInstance3);
	}

	@Test
	void instance() {
		Map<String, String> kubernetesServiceInstance2Metadata = new HashMap<>();
		kubernetesServiceInstance2Metadata.put(TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getName(),
				TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(TEST_SERVICE_3.getMetadata().getLabels());
		KubernetesServiceInstance kubernetesServiceInstance3 = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS_3.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE_3.getMetadata().getName(), TEST_ENDPOINTS_3.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS_3.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata,
				false, TEST_SERVICE_3.getMetadata().getNamespace(), null);
		webTestClient.get().uri("/app/test-svc-3/uid2").exchange().expectBody(KubernetesServiceInstance.class)
				.isEqualTo(kubernetesServiceInstance3);
	}

	@TestConfiguration
	protected static class TestConfig {

		@Bean
		KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider provider = mock(KubernetesNamespaceProvider.class);
			when(provider.getNamespace()).thenReturn("namespace1");
			return provider;
		}

		@Bean
		ApiClient apiClient() {
			WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());
			wireMockServer.start();
			WireMock.configureFor(wireMockServer.port());
			stubFor(get("/api/v1/namespaces/namespace1/endpoints?resourceVersion=0&watch=false")
					.willReturn(aResponse().withStatus(200)
							.withBody(new JSON().serialize(
									new V1EndpointsListBuilder().withMetadata(new V1ListMetaBuilder().build())
											.addToItems(TEST_ENDPOINTS_1, TEST_ENDPOINTS_3).build()))));
			stubFor(get("/api/v1/namespaces/namespace1/services?resourceVersion=0&watch=false")
					.willReturn(aResponse().withStatus(200)
							.withBody(new JSON()
									.serialize(new V1ServiceListBuilder().withMetadata(new V1ListMetaBuilder().build())
											.addToItems(TEST_SERVICE_1, TEST_SERVICE_2, TEST_SERVICE_3).build()))));
			stubFor(get("/api/v1/namespaces/namespace1/endpoints?watch=true").willReturn(aResponse().withStatus(200)));
			stubFor(get("/api/v1/namespaces/namespace1/services?watch=true").willReturn(aResponse().withStatus(200)));
			return new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
		}

//		@Bean
//		KubernetesInformerReactiveDiscoveryClient discoveryClient() {
//			KubernetesInformerReactiveDiscoveryClient discoveryClient =
//				Mockito.mock(KubernetesInformerReactiveDiscoveryClient.class);
//			return discoveryClient;
//		}

	}

}
