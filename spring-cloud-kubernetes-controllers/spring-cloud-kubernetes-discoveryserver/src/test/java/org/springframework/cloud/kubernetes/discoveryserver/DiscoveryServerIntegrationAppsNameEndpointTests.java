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

package org.springframework.cloud.kubernetes.discoveryserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.client.discovery.VisibleKubernetesClientInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = DiscoveryServerIntegrationAppsNameEndpointTests.TestConfig.class,
		properties = { "management.health.livenessstate.enabled=true",
				/* disable kubernetes from liveness and readiness */
				"management.endpoint.health.group.liveness.include=livenessState",
				"management.health.readinessstate.enabled=true",
				"management.endpoint.health.group.readiness.include=readinessState" })
@AutoConfigureWebTestClient
class DiscoveryServerIntegrationAppsNameEndpointTests {

	private static final String NAMESPACE = "namespace";

	private static final SharedInformerFactory SHARED_INFORMER_FACTORY = Mockito.mock(SharedInformerFactory.class);

	private static final V1Service TEST_SERVICE = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-3")
			.namespace(NAMESPACE)
			.putLabelsItem("spring", "true")
			.putLabelsItem("k8s", "true"))
		.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1").type("ClusterIP"))
		.status(new V1ServiceStatus());

	private static final V1Endpoints TEST_ENDPOINTS = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-3").namespace(NAMESPACE))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080).name("http"))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")
				.targetRef(new V1ObjectReferenceBuilder().withUid("uid2").build())));

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void appsName() {
		Map<String, String> metadata = new HashMap<>();
		metadata.put("spring", "true");
		metadata.put("port.http", "8080");
		metadata.put("k8s_namespace", "namespace");
		metadata.put("type", "ClusterIP");
		metadata.put("k8s", "true");

		DefaultKubernetesServiceInstance kubernetesServiceInstance = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE.getMetadata().getName(), TEST_ENDPOINTS.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS.getSubsets().get(0).getPorts().get(0).getPort(), metadata, false,
				TEST_SERVICE.getMetadata().getNamespace(), null, Map.of());

		webTestClient.get()
			.uri("/apps/test-svc-3")
			.exchange()
			.expectBodyList(DefaultKubernetesServiceInstance.class)
			.hasSize(1)
			.contains(kubernetesServiceInstance);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider provider = mock(KubernetesNamespaceProvider.class);
			when(provider.getNamespace()).thenReturn(NAMESPACE);
			return provider;
		}

		@Bean
		KubernetesClientInformerReactiveDiscoveryClient discoveryClient() {
			return new KubernetesClientInformerReactiveDiscoveryClient(kubernetesInformerDiscoveryClient());
		}

		private VisibleKubernetesClientInformerDiscoveryClient kubernetesInformerDiscoveryClient() {

			Lister<V1Service> serviceLister = Util.setupServiceLister(TEST_SERVICE);
			Lister<V1Endpoints> endpointsLister = Util.setupEndpointsLister(TEST_ENDPOINTS);

			return new VisibleKubernetesClientInformerDiscoveryClient(List.of(SHARED_INFORMER_FACTORY),
					List.of(serviceLister), List.of(endpointsLister), null, null, KubernetesDiscoveryProperties.DEFAULT,
					Mockito.mock(CoreV1Api.class), x -> true);
		}

	}

}
