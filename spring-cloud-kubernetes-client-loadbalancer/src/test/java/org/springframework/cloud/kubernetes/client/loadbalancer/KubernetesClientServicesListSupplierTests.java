/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.loadbalancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class KubernetesClientServicesListSupplierTests {

	private static final V1ServiceList SERVICE_LIST = new V1ServiceList().addItemsItem(new V1ServiceBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("service1").withNamespace("default")
					.withResourceVersion("1").addToLabels("beta", "true")
					.addToAnnotations("org.springframework.cloud", "true").withUid("0").build())
			.withSpec(new V1ServiceSpecBuilder()
					.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build()).build())
			.build());

	private static final V1ServiceList SERVICE_LIST_ALL_NAMESPACE = new V1ServiceList()
			.addItemsItem(new V1ServiceBuilder()
					.withMetadata(new V1ObjectMetaBuilder().withName("service1").withNamespace("default")
							.withResourceVersion("1").addToLabels("beta", "true")
							.addToAnnotations("org.springframework.cloud", "true").withUid("0").build())
					.withSpec(new V1ServiceSpecBuilder()
							.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build()).build())
					.build())
			.addItemsItem(new V1ServiceBuilder()
					.withMetadata(new V1ObjectMetaBuilder().withName("service1").withNamespace("test")
							.withResourceVersion("1").withUid("1").build())
					.withSpec(new V1ServiceSpecBuilder()
							.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build(),
									new V1ServicePortBuilder().withPort(443).withName("https").build())
							.build())
					.build());

	private static WireMockServer wireMockServer;

	@BeforeAll
	public static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
	}

	@AfterAll
	public static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	public void afterEach() {
		WireMock.reset();
	}

	@Test
	void getList() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty(LoadBalancerClientFactory.PROPERTY_NAME, "service1");
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");
		CoreV1Api coreV1Api = new CoreV1Api();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(
				new KubernetesLoadBalancerProperties(), KubernetesDiscoveryProperties.DEFAULT);
		KubernetesClientServicesListSupplier listSupplier = new KubernetesClientServicesListSupplier(env, mapper,
				KubernetesDiscoveryProperties.DEFAULT, coreV1Api, kubernetesNamespaceProvider);

		stubFor(get(urlMatching("^/api/v1/namespaces/default/services.*"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SERVICE_LIST))));

		Flux<List<ServiceInstance>> instances = listSupplier.get();

		Map<String, String> metadata = new HashMap<>();
		metadata.put("org.springframework.cloud", "true");
		metadata.put("beta", "true");
		DefaultKubernetesServiceInstance service1 = new DefaultKubernetesServiceInstance("0", "service1",
				"service1.default.svc.cluster.local", 80, metadata, false);
		List<ServiceInstance> services = new ArrayList<>();
		services.add(service1);

		StepVerifier.create(instances).expectNext(services).verifyComplete();
	}

	@Test
	void getListAllNamespaces() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty(LoadBalancerClientFactory.PROPERTY_NAME, "service1");
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				true, 60, false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0);
		CoreV1Api coreV1Api = new CoreV1Api();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(
				new KubernetesLoadBalancerProperties(), kubernetesDiscoveryProperties);
		KubernetesClientServicesListSupplier listSupplier = new KubernetesClientServicesListSupplier(env, mapper,
				kubernetesDiscoveryProperties, coreV1Api, kubernetesNamespaceProvider);

		stubFor(get(urlMatching("^/api/v1/services.*"))
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SERVICE_LIST_ALL_NAMESPACE))));

		Flux<List<ServiceInstance>> instances = listSupplier.get();

		Map<String, String> metadata = new HashMap<>();
		metadata.put("org.springframework.cloud", "true");
		metadata.put("beta", "true");
		DefaultKubernetesServiceInstance service1 = new DefaultKubernetesServiceInstance("0", "service1",
				"service1.default.svc.cluster.local", 80, metadata, false);
		DefaultKubernetesServiceInstance service2 = new DefaultKubernetesServiceInstance("1", "service1",
				"service1.test.svc.cluster.local", 80, new HashMap<>(), false);
		List<ServiceInstance> services = new ArrayList<>();
		services.add(service1);
		services.add(service2);

		StepVerifier.create(instances).expectNext(services).verifyComplete();
	}

}
