/*
 * Copyright 2013-2023 the original author or authors.
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

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
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
import org.springframewok.cloud.kubernetes.discoveryserver.DiscoveryServerController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = DiscoveryServerIntegrationAppsEndpointTest.TestConfig.class)
class DiscoveryServerIntegrationAppsEndpointTest {

	private static final String NAMESPACE = "namespace";

	private static final SharedInformerFactory SHARED_INFORMER_FACTORY = Mockito.mock(SharedInformerFactory.class);

	private static final V1Service TEST_SERVICE_1 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE))
		.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service TEST_SERVICE_2 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-2").namespace(NAMESPACE).putLabelsItem("spring", "true")
			.putLabelsItem("k8s", "true"))
		.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints TEST_ENDPOINTS_1 = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080).name("http"))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")
				.targetRef(new V1ObjectReferenceBuilder().withUid("uid1").build())));

	private static final V1Endpoints TEST_ENDPOINTS_2 = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-2").namespace(NAMESPACE))
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
		kubernetesServiceInstance2Metadata.put(TEST_ENDPOINTS_2.getSubsets().get(0).getPorts().get(0).getName(),
			TEST_ENDPOINTS_2.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(TEST_SERVICE_2.getMetadata().getLabels());

		KubernetesServiceInstance kubernetesServiceInstance1 = new DefaultKubernetesServiceInstance(
			TEST_ENDPOINTS_1.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
			TEST_SERVICE_1.getMetadata().getName(), TEST_ENDPOINTS_1.getSubsets().get(0).getAddresses().get(0).getIp(),
			TEST_ENDPOINTS_1.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance1Metadata,
			false, TEST_SERVICE_1.getMetadata().getNamespace(), null);
		KubernetesServiceInstance kubernetesServiceInstance3 = new DefaultKubernetesServiceInstance(
			TEST_ENDPOINTS_2.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(),
			TEST_SERVICE_2.getMetadata().getName(), TEST_ENDPOINTS_2.getSubsets().get(0).getAddresses().get(0).getIp(),
			TEST_ENDPOINTS_2.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata,
			false, TEST_SERVICE_2.getMetadata().getNamespace(), null);

		webTestClient.get().uri("/apps").exchange().expectBodyList(DiscoveryServerController.Service.class).hasSize(2).contains(
			new DiscoveryServerController.Service(TEST_SERVICE_1.getMetadata().getName(),
				Collections.singletonList(kubernetesServiceInstance1)),
			new DiscoveryServerController.Service(TEST_SERVICE_2.getMetadata().getName(),
				Collections.singletonList(kubernetesServiceInstance3)));
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
		KubernetesInformerReactiveDiscoveryClient discoveryClient() {
			return new KubernetesInformerReactiveDiscoveryClient(kubernetesInformerDiscoveryClient());
		}

		private KubernetesInformerDiscoveryClient kubernetesInformerDiscoveryClient() {

			Lister<V1Service> serviceLister = Util.setupServiceLister(TEST_SERVICE_1, TEST_SERVICE_2);
			Lister<V1Endpoints> endpointsLister = Util.setupEndpointsLister(TEST_ENDPOINTS_1, TEST_ENDPOINTS_2);

            return new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);
		}
	}
}
