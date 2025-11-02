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

import java.util.Collections;
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
		classes = DiscoveryServerIntegrationAppsEndpointTests.TestConfig.class,
		properties = { "management.health.livenessstate.enabled=true",
				/* disable kubernetes from liveness and readiness */
				"management.endpoint.health.group.liveness.include=livenessState",
				"management.health.readinessstate.enabled=true",
				"management.endpoint.health.group.readiness.include=readinessState" })
@AutoConfigureWebTestClient
class DiscoveryServerIntegrationAppsEndpointTests {

	private static final String NAMESPACE = "namespace";

	private static final SharedInformerFactory SHARED_INFORMER_FACTORY = Mockito.mock(SharedInformerFactory.class);

	private static final V1Service TEST_SERVICE_A = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE))
		.spec(new V1ServiceSpec().type("ClusterIP"));

	private static final V1Service TEST_SERVICE_B = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-2")
			.namespace(NAMESPACE)
			.putLabelsItem("spring", "true")
			.putLabelsItem("k8s", "true"))
		.spec(new V1ServiceSpec().type("ClusterIP"));

	private static final V1Endpoints TEST_ENDPOINTS_A = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080).name("http"))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")
				.targetRef(new V1ObjectReferenceBuilder().withUid("uid1").build())));

	private static final V1Endpoints TEST_ENDPOINTS_B = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-2").namespace(NAMESPACE))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080).name("http"))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")
				.targetRef(new V1ObjectReferenceBuilder().withUid("uid2").build())));

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void apps() {
		Map<String, String> metadataA = new HashMap<>();
		metadataA.put("type", "ClusterIP");
		metadataA.put("port.http", "8080");
		metadataA.put("k8s_namespace", "namespace");

		Map<String, String> metadataB = new HashMap<>(metadataA);
		metadataB.put("spring", "true");
		metadataB.put("k8s", "true");

		DefaultKubernetesServiceInstance kubernetesServiceInstance1 = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS_A.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE_A.getMetadata().getName(),
				TEST_ENDPOINTS_A.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS_A.getSubsets().get(0).getPorts().get(0).getPort(), metadataA, false,
				TEST_SERVICE_A.getMetadata().getNamespace(), null, Map.of());

		DefaultKubernetesServiceInstance kubernetesServiceInstance2 = new DefaultKubernetesServiceInstance(
				TEST_ENDPOINTS_B.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
				TEST_SERVICE_B.getMetadata().getName(),
				TEST_ENDPOINTS_B.getSubsets().get(0).getAddresses().get(0).getIp(),
				TEST_ENDPOINTS_B.getSubsets().get(0).getPorts().get(0).getPort(), metadataB, false,
				TEST_SERVICE_B.getMetadata().getNamespace(), null, Map.of());

		webTestClient.get()
			.uri("/apps")
			.exchange()
			.expectBodyList(Util.InstanceForTest.class)
			.hasSize(2)
			.contains(
					new Util.InstanceForTest(TEST_SERVICE_A.getMetadata().getName(),
							Collections.singletonList(kubernetesServiceInstance1)),
					new Util.InstanceForTest(TEST_SERVICE_B.getMetadata().getName(),
							Collections.singletonList(kubernetesServiceInstance2)));
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

			Lister<V1Service> serviceLister = Util.setupServiceLister(TEST_SERVICE_A, TEST_SERVICE_B);
			Lister<V1Endpoints> endpointsLister = Util.setupEndpointsLister(TEST_ENDPOINTS_A, TEST_ENDPOINTS_B);

			return new VisibleKubernetesClientInformerDiscoveryClient(List.of(SHARED_INFORMER_FACTORY),
					List.of(serviceLister), List.of(endpointsLister), null, null, KubernetesDiscoveryProperties.DEFAULT,
					Mockito.mock(CoreV1Api.class), x -> true);
		}

	}

}
