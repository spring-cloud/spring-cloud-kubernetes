/*
 * Copyright 2013-2024 the original author or authors.
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
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesLoadBalancerProperties;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory.PROPERTY_NAME;

/**
 * @author Ryan Baxter
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesClientServicesListSupplierTests {

	private static final V1Service SERVICE_A_DEFAULT_NAMESPACE = new V1ServiceBuilder()
		.withMetadata(new V1ObjectMetaBuilder().withName("service-a")
			.withNamespace("default")
			.withUid("0")
			.addToLabels("beta", "true")
			.addToAnnotations("org.springframework.cloud", "true")
			.build())
		.withSpec(new V1ServiceSpecBuilder().withType("V1Service")
			.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build())
			.build())
		.build();

	private static final V1Service SERVICE_A_TEST_NAMESPACE = new V1ServiceBuilder()
		.withMetadata(new V1ObjectMetaBuilder().withName("service-a").withNamespace("test").withUid("1").build())
		.withSpec(new V1ServiceSpecBuilder().withType("V1Service")
			.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http").build(),
					new V1ServicePortBuilder().withPort(443).withName("https").build())
			.build())
		.build();

	private static final V1ServiceList SINGLE_NAMESPACE_SERVICES = new V1ServiceList()
		.addItemsItem(SERVICE_A_DEFAULT_NAMESPACE);

	private static final V1ServiceList SERVICE_LIST_ALL_NAMESPACE = new V1ServiceList()
		.addItemsItem(SERVICE_A_DEFAULT_NAMESPACE)
		.addItemsItem(SERVICE_A_TEST_NAMESPACE);

	private static final V1ServiceList SERVICE_A_DEFAULT_NAMESPACE_SELECTIVE_NAMESPACES = new V1ServiceList()
		.addItemsItem(SERVICE_A_DEFAULT_NAMESPACE);

	private static final V1ServiceList SERVICE_A_TEST_NAMESPACE_SELECTIVE_NAMESPACES = new V1ServiceList()
		.addItemsItem(SERVICE_A_TEST_NAMESPACE);

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		Configuration.setDefaultApiClient(client);
	}

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	@Test
	void singleNamespaceTest(CapturedOutput output) {
		MockEnvironment env = new MockEnvironment().withProperty(PROPERTY_NAME, "service-a");

		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");

		CoreV1Api coreV1Api = new CoreV1Api();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(
				new KubernetesLoadBalancerProperties(), KubernetesDiscoveryProperties.DEFAULT);

		boolean allNamespaces = false;
		Set<String> selectiveNamespaces = Set.of();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(443, 8443, 12345), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesClientServicesListSupplier listSupplier = new KubernetesClientServicesListSupplier(env, mapper,
				discoveryProperties, coreV1Api, kubernetesNamespaceProvider);

		stubFor(get(urlEqualTo("/api/v1/namespaces/default/services?fieldSelector=metadata.name%3Dservice-a"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SINGLE_NAMESPACE_SERVICES))));

		Flux<List<ServiceInstance>> instances = listSupplier.get();

		Map<String, String> metadata = Map.of("org.springframework.cloud", "true", "beta", "true", "k8s_namespace",
				"default", "type", "V1Service");
		DefaultKubernetesServiceInstance serviceA = new DefaultKubernetesServiceInstance("0", "service-a",
				"service-a.default.svc.cluster.local", 80, metadata, false);
		List<ServiceInstance> services = new ArrayList<>();
		services.add(serviceA);

		StepVerifier.create(instances).expectNext(services).verifyComplete();
		Assertions.assertTrue(output.getOut().contains("serviceID : service-a"));
		Assertions.assertTrue(output.getOut().contains("discovering services in namespace : default"));
	}

	@Test
	void singleNamespaceNoServicePresentTest(CapturedOutput output) {
		MockEnvironment env = new MockEnvironment().withProperty(PROPERTY_NAME, "service-a");

		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");

		CoreV1Api coreV1Api = new CoreV1Api();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(
				new KubernetesLoadBalancerProperties(), KubernetesDiscoveryProperties.DEFAULT);

		boolean allNamespaces = false;
		Set<String> selectiveNamespaces = Set.of();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(443, 8443, 12345), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesClientServicesListSupplier listSupplier = new KubernetesClientServicesListSupplier(env, mapper,
				discoveryProperties, coreV1Api, kubernetesNamespaceProvider);

		stubFor(get(urlEqualTo("/api/v1/namespaces/default/services?fieldSelector=metadata.name%3Dservice-a"))
			.willReturn(aResponse().withStatus(404)));

		Flux<List<ServiceInstance>> instances = listSupplier.get();
		List<ServiceInstance> services = List.of();

		StepVerifier.create(instances).expectNext(services).verifyComplete();
		Assertions.assertTrue(output.getOut().contains("serviceID : service-a"));
		Assertions.assertTrue(output.getOut().contains("discovering services in namespace : default"));
		Assertions.assertTrue(output.getOut().contains("Error retrieving service with name service-a"));
	}

	@Test
	void allNamespacesTest(CapturedOutput output) {
		MockEnvironment env = new MockEnvironment().withProperty(PROPERTY_NAME, "service-a");
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("default");

		boolean allNamespaces = true;
		Set<String> selectiveNamespaces = Set.of();
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(443, 8443, 12345), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		CoreV1Api coreV1Api = new CoreV1Api();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(
				new KubernetesLoadBalancerProperties(), discoveryProperties);

		KubernetesClientServicesListSupplier listSupplier = new KubernetesClientServicesListSupplier(env, mapper,
				discoveryProperties, coreV1Api, kubernetesNamespaceProvider);

		stubFor(get(urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3Dservice-a"))
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SERVICE_LIST_ALL_NAMESPACE))));

		Flux<List<ServiceInstance>> instances = listSupplier.get();

		Map<String, String> metadata = Map.of("org.springframework.cloud", "true", "beta", "true", "k8s_namespace",
				"default", "type", "V1Service");
		DefaultKubernetesServiceInstance serviceADefaultNamespace = new DefaultKubernetesServiceInstance("0",
				"service-a", "service-a.default.svc.cluster.local", 80, metadata, false);
		DefaultKubernetesServiceInstance serviceATestNamespace = new DefaultKubernetesServiceInstance("1", "service-a",
				"service-a.test.svc.cluster.local", 80, Map.of("k8s_namespace", "test", "type", "V1Service"), false);
		List<ServiceInstance> services = new ArrayList<>();
		services.add(serviceADefaultNamespace);
		services.add(serviceATestNamespace);

		StepVerifier.create(instances).expectNext(services).verifyComplete();
		Assertions.assertTrue(output.getOut().contains("discovering services in all namespaces"));
	}

	@Test
	void selectiveNamespacesTest(CapturedOutput output) {
		MockEnvironment env = new MockEnvironment().withProperty(PROPERTY_NAME, "service-a");
		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);

		boolean allNamespaces = false;
		Set<String> selectiveNamespaces = Set.of("default", "test", "no-service");
		KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties(true, allNamespaces,
				selectiveNamespaces, true, 60, false, null, Set.of(443, 8443, 12345), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		CoreV1Api coreV1Api = new CoreV1Api();
		KubernetesClientServiceInstanceMapper mapper = new KubernetesClientServiceInstanceMapper(
				new KubernetesLoadBalancerProperties(), discoveryProperties);

		KubernetesClientServicesListSupplier listSupplier = new KubernetesClientServicesListSupplier(env, mapper,
				discoveryProperties, coreV1Api, kubernetesNamespaceProvider);

		stubFor(get(urlEqualTo("/api/v1/namespaces/default/services?fieldSelector=metadata.name%3Dservice-a"))
			.willReturn(aResponse().withStatus(200)
				.withBody(new JSON().serialize(SERVICE_A_DEFAULT_NAMESPACE_SELECTIVE_NAMESPACES))));

		stubFor(get(urlEqualTo("/api/v1/namespaces/test/services?fieldSelector=metadata.name%3Dservice-a"))
			.willReturn(aResponse().withStatus(200)
				.withBody(new JSON().serialize(SERVICE_A_TEST_NAMESPACE_SELECTIVE_NAMESPACES))));

		stubFor(get(urlEqualTo("/api/v1/namespaces/no-service/services?fieldSelector=metadata.name%3Dservice-a"))
			.willReturn(aResponse().withStatus(404)));

		Flux<List<ServiceInstance>> instances = listSupplier.get();

		Map<String, String> metadata = Map.of("org.springframework.cloud", "true", "beta", "true", "k8s_namespace",
				"default", "type", "V1Service");
		DefaultKubernetesServiceInstance serviceADefaultNamespace = new DefaultKubernetesServiceInstance("0",
				"service-a", "service-a.default.svc.cluster.local", 80, metadata, false);
		DefaultKubernetesServiceInstance serviceATestNamespace = new DefaultKubernetesServiceInstance("1", "service-a",
				"service-a.test.svc.cluster.local", 80, Map.of("k8s_namespace", "test", "type", "V1Service"), false);
		List<ServiceInstance> services = new ArrayList<>();
		services.add(serviceADefaultNamespace);
		services.add(serviceATestNamespace);

		StepVerifier.create(instances).expectNext(services).verifyComplete();
		Assertions.assertTrue(
				output.getOut().contains("Error retrieving service with name service-a in namespace : no-service"));
		Assertions.assertTrue(
				output.getOut().contains("discovering services in selective namespaces : [default, no-service, test]"));
	}

}
