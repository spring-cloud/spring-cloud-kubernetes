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

package org.springframewok.cloud.kubernetes.discoveryserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointPort;
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
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = DiscoveryServerIntegrationTests.TestConfig.class,
properties = {"debug=true"})
public class DiscoveryServerIntegrationTests {

	private static final V1Service testService1 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
		.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());
	private static final V1Endpoints testEndpoints1 = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080).name("http"))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2").targetRef(new V1ObjectReferenceBuilder().withUid("uid1").build())));

	private static final V1Service testService2 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"))
		.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service testService3 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1").putLabelsItem("spring", "true")
			.putLabelsItem("k8s", "true"))
		.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());
	private static final V1Endpoints testEndpoints3 = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1"))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080).name("http"))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2").targetRef(new V1ObjectReferenceBuilder().withUid("uid2").build())));


	private static WireMockServer wireMockServer;

	@Autowired
	WebTestClient webTestClient;

	@Test
	void apps(){
		Map<String, String> kubernetesServiceInstance1Metadata = new HashMap<>();
		kubernetesServiceInstance1Metadata.put(testEndpoints1.getSubsets().get(0).getPorts().get(0).getName(), testEndpoints1.getSubsets().get(0).getPorts().get(0).getPort().toString());

		Map<String, String> kubernetesServiceInstance2Metadata = new HashMap<>();
		kubernetesServiceInstance2Metadata.put(testEndpoints3.getSubsets().get(0).getPorts().get(0).getName(), testEndpoints3.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(testService3.getMetadata().getLabels());

		KubernetesServiceInstance kubernetesServiceInstance1 = new KubernetesServiceInstance(testEndpoints1.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(), testService1.getMetadata().getName(), testEndpoints1.getSubsets().get(0).getAddresses().get(0).getIp(), testEndpoints1.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance1Metadata, false, testService1.getMetadata().getNamespace(), null);
		KubernetesServiceInstance kubernetesServiceInstance3 = new KubernetesServiceInstance(testEndpoints3.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(), testService3.getMetadata().getName(), testEndpoints3.getSubsets().get(0).getAddresses().get(0).getIp(), testEndpoints3.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata, false, testService3.getMetadata().getNamespace(), null);

		webTestClient.get().uri("/apps").exchange().expectBodyList(KubernetesService.class).hasSize(2).contains(new KubernetesService(testService1.getMetadata().getName(),
			Collections.singletonList(kubernetesServiceInstance1)), new KubernetesService(testService3.getMetadata().getName(), Collections.singletonList(kubernetesServiceInstance3)));
	}

	@Test
	void appsName() {
		Map<String, String> kubernetesServiceInstance2Metadata = new HashMap<>();
		kubernetesServiceInstance2Metadata.put(testEndpoints3.getSubsets().get(0).getPorts().get(0).getName(), testEndpoints3.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(testService3.getMetadata().getLabels());
		KubernetesServiceInstance kubernetesServiceInstance3 = new KubernetesServiceInstance(testEndpoints3.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(), testService3.getMetadata().getName(), testEndpoints3.getSubsets().get(0).getAddresses().get(0).getIp(), testEndpoints3.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata, false, testService3.getMetadata().getNamespace(), null);
		webTestClient.get().uri("/apps/test-svc-3").exchange().expectBodyList(KubernetesServiceInstance.class).hasSize(1).contains(kubernetesServiceInstance3);
	}

	@Test
	void instance() {
		Map<String, String> kubernetesServiceInstance2Metadata = new HashMap<>();
		kubernetesServiceInstance2Metadata.put(testEndpoints3.getSubsets().get(0).getPorts().get(0).getName(), testEndpoints3.getSubsets().get(0).getPorts().get(0).getPort().toString());
		kubernetesServiceInstance2Metadata.putAll(testService3.getMetadata().getLabels());
		KubernetesServiceInstance kubernetesServiceInstance3 = new KubernetesServiceInstance(testEndpoints3.getSubsets().get(0).getAddresses().get(0).getTargetRef().getUid(), testService3.getMetadata().getName(), testEndpoints3.getSubsets().get(0).getAddresses().get(0).getIp(), testEndpoints3.getSubsets().get(0).getPorts().get(0).getPort(), kubernetesServiceInstance2Metadata, false, testService3.getMetadata().getNamespace(), null);
		webTestClient.get().uri("/app/test-svc-3/uid2").exchange().expectBody(KubernetesServiceInstance.class).isEqualTo(kubernetesServiceInstance3);
	}

	@SpringBootApplication
	protected static class TestConfig {

		@Bean
		public KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider provider = mock(KubernetesNamespaceProvider.class);
			when(provider.getNamespace()).thenReturn("namespace1");
			return provider;
		}

		@Bean
		public ApiClient apiClient() {
			wireMockServer = new WireMockServer(options().dynamicPort());
			wireMockServer.start();
			WireMock.configureFor(wireMockServer.port());
			stubFor(get("/api/v1/namespaces/namespace1/endpoints?resourceVersion=0&watch=false")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1EndpointsListBuilder()
					.withMetadata(new V1ListMetaBuilder().withNewResourceVersion("0").build()).addToItems(testEndpoints1, testEndpoints3).build()))));
			stubFor(get("/api/v1/namespaces/namespace1/services?resourceVersion=0&watch=false")
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1ServiceListBuilder()
					.withMetadata(new V1ListMetaBuilder().withNewResourceVersion("0").build()).addToItems(testService1, testService2, testService3).build()))));
			stubFor(get("/api/v1/namespaces/namespace1/endpoints?watch=true")
				.willReturn(aResponse().withStatus(200)));
			stubFor(get("/api/v1/namespaces/namespace1/services?watch=true")
				.willReturn(aResponse().withStatus(200)));
			ApiClient apiClient = new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build();
			return apiClient;
		}

	}

	public static class KubernetesService {

		private String name;

		private List<KubernetesServiceInstance> serviceInstances;

		public KubernetesService() { }

		public KubernetesService(String name, List<KubernetesServiceInstance> serviceInstances) {
			this.name = name;
			this.serviceInstances = serviceInstances;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<KubernetesServiceInstance> getServiceInstances() {
			return serviceInstances;
		}

		public void setServiceInstances(List<KubernetesServiceInstance> serviceInstances) {
			this.serviceInstances = serviceInstances;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			KubernetesService service = (KubernetesService) o;
			return Objects.equals(getName(), service.getName()) && Objects.equals(getServiceInstances(), service.getServiceInstances());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getName(), getServiceInstances());
		}

		@Override
		public String toString() {
			return "KubernetesService{" +
				"name='" + name + '\'' +
				", serviceInstances=" + serviceInstances +
				'}';
		}
	}
}
