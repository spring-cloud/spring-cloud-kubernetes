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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static io.kubernetes.client.util.Namespaces.NAMESPACE_ALL;

/**
 * @author Ryan Baxter
 */
class KubernetesClientInformerReactiveDiscoveryClientTests {

	private static final CoreV1Api CORE_V1_API = Mockito.mock(CoreV1Api.class);

	// default constructor partitions by namespace
	private Cache<V1Service> serviceCache = new Cache<>();

	// default constructor partitions by namespace
	private Cache<V1Endpoints> endpointsCache = new Cache<>();

	private static final String NAMESPACE_1 = "namespace1";

	private static final String NAMESPACE_2 = "namespace2";

	private final SharedInformerFactory sharedInformerFactory = Mockito.mock(SharedInformerFactory.class);

	private static final V1Service TEST_SERVICE_1 = new V1Service().spec(new V1ServiceSpec().type("ClusterIP"))
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE_1));

	private static final V1Service TEST_SERVICE_2 = new V1Service().spec(new V1ServiceSpec().type("ClusterIP"))
		.metadata(new V1ObjectMeta().name("test-svc-2").namespace(NAMESPACE_2));

	// same name as TEST_SERVICE_1, to test distinct
	private static final V1Service TEST_SERVICE_3 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-2").namespace(NAMESPACE_2));

	private static final V1Endpoints TEST_ENDPOINTS_1 = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE_1))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
			.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	@AfterEach
	void afterEach() {
		serviceCache = new Cache<>();
		endpointsCache = new Cache<>();
	}

	@Test
	void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_ALL, TEST_SERVICE_1, TEST_SERVICE_2,
				TEST_SERVICE_3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		StepVerifier.create(discoveryClient.getServices())
			.expectNext(TEST_SERVICE_1.getMetadata().getName(), TEST_SERVICE_2.getMetadata().getName())
			.expectComplete()
			.verify();

	}

	@Test
	void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_1, TEST_SERVICE_1, TEST_SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, KubernetesDiscoveryProperties.DEFAULT, CORE_V1_API,
						x -> true));

		StepVerifier.create(discoveryClient.getServices())
			.expectNext(TEST_SERVICE_1.getMetadata().getName())
			.expectComplete()
			.verify();

	}

	@Test
	void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_ALL, TEST_SERVICE_1, TEST_SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(TEST_ENDPOINTS_1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		StepVerifier.create(discoveryClient.getInstances("test-svc-1"))
			.expectNext(new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
					Map.of("type", "ClusterIP", "port.<unset>", "8080", "k8s_namespace", "namespace1"), false,
					"namespace1", null, Map.of()))
			.expectComplete()
			.verify();

	}

	@Test
	void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_1, TEST_SERVICE_1, TEST_SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(TEST_ENDPOINTS_1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		StepVerifier.create(discoveryClient.getInstances("test-svc-1"))
			.expectNext(new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
					Map.of("type", "ClusterIP", "port.<unset>", "8080", "k8s_namespace", "namespace1"), false,
					"namespace1", null, Map.of()))
			.expectComplete()
			.verify();

	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - service-a in namespace-a exists
	 *     - service-b in namespace-b exists
	 *
	 *     As such, both services are found.
	 * </pre>
	 */
	@Test
	void testAllNamespacesTwoServicesPresent() {
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		boolean allNamespaces = true;
		V1Service serviceA = new V1Service().metadata(new V1ObjectMeta().name("service-a").namespace("namespace-a"));
		V1Service serviceB = new V1Service().metadata(new V1ObjectMeta().name("service-b").namespace("namespace-b"));
		serviceCache.add(serviceA);
		serviceCache.add(serviceB);

		Lister<V1Service> serviceLister = new Lister<>(serviceCache).namespace(NAMESPACE_ALL);
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true,
				allNamespaces, Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		List<String> result = discoveryClient.getServices().collectList().block();
		Assertions.assertThat(result).containsExactlyInAnyOrder("service-a", "service-b");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - service-a in namespace-a exists
	 *     - service-b in namespace-b exists
	 *     - service lister exists in namespace-a
	 *
	 *     As such, one service is found.
	 * </pre>
	 */
	@Test
	void testSingleNamespaceTwoServicesPresent() {
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		boolean allNamespaces = false;
		V1Service serviceA = new V1Service().metadata(new V1ObjectMeta().name("service-a").namespace("namespace-a"));
		V1Service serviceB = new V1Service().metadata(new V1ObjectMeta().name("service-b").namespace("namespace-b"));
		serviceCache.add(serviceA);
		serviceCache.add(serviceB);

		Lister<V1Service> serviceLister = new Lister<>(serviceCache).namespace("namespace-a");
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true,
				allNamespaces, Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		List<String> result = discoveryClient.getServices().collectList().block();
		Assertions.assertThat(result).containsExactlyInAnyOrder("service-a");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - endpoints-X in namespace-a exists
	 *     - endpoints-X in namespace-b exists
	 *
	 *     As such, both endpoints are found.
	 * </pre>
	 */
	@Test
	void testAllNamespacesTwoEndpointsPresent() {
		boolean allNamespaces = true;

		V1Service serviceXNamespaceA = new V1Service()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-a"))
			.spec(new V1ServiceSpecBuilder().withType("ClusterIP").build());
		V1Service serviceXNamespaceB = new V1Service()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-b"))
			.spec(new V1ServiceSpecBuilder().withType("ClusterIP").build());
		serviceCache.add(serviceXNamespaceA);
		serviceCache.add(serviceXNamespaceB);

		V1Endpoints endpointsXNamespaceA = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-a"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
				.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
		V1Endpoints endpointsXNamespaceB = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-b"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
				.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));
		endpointsCache.add(endpointsXNamespaceA);
		endpointsCache.add(endpointsXNamespaceB);

		Lister<V1Endpoints> endpointsLister = new Lister<>(endpointsCache, NAMESPACE_ALL);
		Lister<V1Service> serviceLister = new Lister<>(serviceCache, NAMESPACE_ALL);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true,
				allNamespaces, Set.of(), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		List<ServiceInstance> result = discoveryClient.getInstances("endpoints-x").collectList().block();
		Assertions.assertThat(result.stream().map(ServiceInstance::getHost).toList())
			.containsExactlyInAnyOrder("1.1.1.1", "2.2.2.2");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - endpoints-X in namespace-a exists
	 *     - endpoints-X in namespace-b exists
	 *
	 *     We search in namespace-a, only. As such, single endpoints is found.
	 * </pre>
	 */
	@Test
	void testAllSingleTwoEndpointsPresent() {
		boolean allNamespaces = true;

		V1Service serviceXNamespaceA = new V1Service()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-a"))
			.spec(new V1ServiceSpecBuilder().withType("ClusterIP").build());
		V1Service serviceXNamespaceB = new V1Service()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-b"))
			.spec(new V1ServiceSpecBuilder().withType("ClusterIP").build());
		serviceCache.add(serviceXNamespaceA);
		serviceCache.add(serviceXNamespaceB);

		V1Endpoints endpointsXNamespaceA = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-a"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
				.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
		V1Endpoints endpointsXNamespaceB = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("endpoints-x").namespace("namespace-b"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
				.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));
		endpointsCache.add(endpointsXNamespaceA);
		endpointsCache.add(endpointsXNamespaceB);

		Lister<V1Endpoints> endpointsLister = new Lister<>(endpointsCache).namespace("namespace-a");
		Lister<V1Service> serviceLister = new Lister<>(serviceCache).namespace("namespace-a");

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true,
				allNamespaces, Set.of(), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		KubernetesClientInformerReactiveDiscoveryClient discoveryClient = new KubernetesClientInformerReactiveDiscoveryClient(
				new KubernetesClientInformerDiscoveryClient(List.of(sharedInformerFactory), List.of(serviceLister),
						List.of(endpointsLister), null, null, kubernetesDiscoveryProperties, CORE_V1_API, x -> true));

		List<ServiceInstance> result = discoveryClient.getInstances("endpoints-x").collectList().block();
		Assertions.assertThat(result.stream().map(ServiceInstance::getHost).toList())
			.containsExactlyInAnyOrder("1.1.1.1");
	}

	@Test
	void testOrder() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("a", "b"),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 57, false, true, null);

		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_ALL, TEST_SERVICE_1, TEST_SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(TEST_ENDPOINTS_1);

		KubernetesClientInformerDiscoveryClient discoveryClient = new KubernetesClientInformerDiscoveryClient(
				List.of(sharedInformerFactory), List.of(serviceLister), List.of(endpointsLister), null, null,
				properties, CORE_V1_API, x -> true);

		Assertions.assertThat(discoveryClient.getOrder()).isEqualTo(57);
	}

	private Lister<V1Service> setupServiceLister(String namespace, V1Service... services) {
		Lister<V1Service> serviceLister = new Lister<>(serviceCache, namespace);
		for (V1Service svc : services) {
			serviceCache.add(svc);
		}
		return serviceLister;
	}

	private Lister<V1Endpoints> setupEndpointsLister(V1Endpoints... endpoints) {
		Lister<V1Endpoints> endpointsLister = new Lister<>(endpointsCache);
		for (V1Endpoints ep : endpoints) {
			endpointsCache.add(ep);
		}
		return endpointsLister;
	}

}
