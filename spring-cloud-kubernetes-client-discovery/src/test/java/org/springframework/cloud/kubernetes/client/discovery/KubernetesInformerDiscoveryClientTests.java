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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.CoreV1EndpointPortBuilder;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointAddressBuilder;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1EndpointSubsetBuilder;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class KubernetesInformerDiscoveryClientTests {

	private static final V1Service SERVICE_1 = service("test-svc-1", "namespace1", Map.of());

	private static final V1Service SERVICE_2 = service("test-svc-1", "namespace2", Map.of());

	private static final V1Service SERVICE_3 = service("test-svc-3", "namespace1",
			Map.of("spring", "true", "k8s", "true"));

	private static final V1Service SERVICE_4 = service("test-svc-1", "namespace1", Map.of("secured", "true"));

	private static final V1Service SERVICE_5 = service("test-svc-1", "namespace1", Map.of("primary-port-name", "oops"));

	private static final V1Service SERVICE_6 = service("test-svc-1", "namespace1",
			Map.of("primary-port-name", "https"));

	private static final SharedInformerFactory SHARED_INFORMER_FACTORY = Mockito.mock(SharedInformerFactory.class);

	private static final V1Endpoints ENDPOINTS_1 = endpointsReadyAddress("test-svc-1", "namespace1");

	private static final V1Endpoints ENDPOINTS_2 = endpointsReadyAddress("test-svc-1", "namespace2");

	private static final V1Endpoints ENDPOINTS_3 = endpointsReadyAddress("test-svc-3", "namespace1");

	private static final V1Endpoints ENDPOINTS_NOT_READY_ADDRESS = endpointsNotReadyAddress();

	private static final V1Endpoints ENDPOINTS_NO_PORTS = endpointsNoPorts();

	private static final V1Endpoints ENDPOINTS_NO_UNSET_PORT_NAME = endpointsNoUnsetPortName();

	private static final V1Endpoints ENDPOINTS_WITH = endpointsWithMultiplePorts();

	private static final V1Endpoints ENDPOINTS_WITH_MULTIPLE_PORTS_NO_HTTPS = endpointsWithMultiplePortsNoHttps();

	private static final V1Endpoints ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES = endpointsMultiplePortsWithoutSupportedPortNames();

	private static final KubernetesDiscoveryProperties ALL_NAMESPACES = properties(true, Map.of());

	private static final KubernetesDiscoveryProperties NOT_ALL_NAMESPACES = properties(false, Map.of());

	@Test
	void testServiceWithUnsetPortNames() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NO_UNSET_PORT_NAME);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray())
				.containsOnly(new DefaultKubernetesServiceInstance(null, "test-svc-1", "1.1.1.1", 80,
						Map.of("port.<unset>", "80", "k8s_namespace", "namespace1", "type", "ClusterIP"), false,
						"namespace1", null));
	}

	@Test
	void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NO_UNSET_PORT_NAME);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_1.getMetadata().getName(),
				SERVICE_2.getMetadata().getName());

	}

	@Test
	void testDiscoveryWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2, SERVICE_3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NO_UNSET_PORT_NAME);

		Map<String, String> labels = Map.of("k8s", "true", "spring", "true");
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = properties(true, labels);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_3.getMetadata().getName());

	}

	@Test
	void testDiscoveryInstancesWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2, SERVICE_3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1, ENDPOINTS_3);

		Map<String, String> labels = Map.of("k8s", "true", "spring", "true");
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = properties(true, labels);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray()).isEmpty();
		assertThat(discoveryClient.getInstances("test-svc-3").toArray())
				.containsOnly(new DefaultKubernetesServiceInstance(
						null, "test-svc-3", "2.2.2.2", 8080, Map.of("spring", "true", "port.<unset>", "8080", "k8s",
								"true", "k8s_namespace", "namespace1", "type", "ClusterIP"),
						false, "namespace1", null));
	}

	@Test
	void testDiscoveryInstancesWithSecuredServiceByAnnotations() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_4);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);
		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_4.getMetadata().getName());
		ServiceInstance serviceInstance = discoveryClient.getInstances(SERVICE_4.getMetadata().getName()).get(0);
		assertThat(serviceInstance.isSecure()).isTrue();
	}

	@Test
	void testDiscoveryInstancesWithSecuredServiceByLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_4);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_4.getMetadata().getName());
		ServiceInstance serviceInstance = discoveryClient.getInstances(SERVICE_4.getMetadata().getName()).get(0);
		assertThat(serviceInstance.isSecure()).isTrue();
	}

	@Test
	void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NO_UNSET_PORT_NAME);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_1.getMetadata().getName());

	}

	@Test
	void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
						Map.of("port.<unset>", "8080", "k8s_namespace", "namespace1", "type", "ClusterIP"), false,
						"namespace1", null));
	}

	@Test
	void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
						Map.of("port.<unset>", "8080", "k8s_namespace", "namespace1", "type", "ClusterIP"), false,
						"namespace1", null));
	}

	@Test
	void testDiscoveryGetInstanceWithoutReadyAddressesShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NOT_READY_ADDRESS);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void testDiscoveryGetInstanceWithNotReadyAddressesIncludedShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NOT_READY_ADDRESS);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, true, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
						Map.of("port.<unset>", "8080", "k8s_namespace", "namespace1", "type", "ClusterIP"), false,
						"namespace1", null));
	}

	@Test
	void instanceWithoutEndpointsShouldBeSkipped() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void instanceWithoutPortsWillNotBeSkipped() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NO_PORTS);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new DefaultKubernetesServiceInstance(null, "test-svc-1", "1.1.1.1", 0,
						Map.of("k8s_namespace", "namespace1", "type", "ClusterIP"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndPrimaryPortNameConfiguredWithLabelShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_6);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance(
				null, "test-svc-1", "1.1.1.1", 443, Map.of("port.http", "80", "primary-port-name", "https",
						"port.https", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"),
				true, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_5);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1"))
				.containsOnly(new DefaultKubernetesServiceInstance(
						null, "test-svc-1", "1.1.1.1", 80, Map.of("port.tcp1", "80", "primary-port-name", "oops",
								"port.tcp2", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"),
						false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndGenericPrimaryPortNameConfiguredShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance(null,
				"test-svc-1", "1.1.1.1", 443,
				Map.of("port.http", "80", "port.https", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"),
				true, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance(null,
				"test-svc-1", "1.1.1.1", 80,
				Map.of("port.tcp1", "80", "port.tcp2", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"),
				false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttpsPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance(null,
				"test-svc-1", "1.1.1.1", 443,
				Map.of("port.http", "80", "port.https", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"),
				true, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttpPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH_MULTIPLE_PORTS_NO_HTTPS);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance(null,
				"test-svc-1", "1.1.1.1", 80,
				Map.of("port.http", "80", "port.tcp", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"), false,
				"namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutAnyConfigurationShouldPickTheFirstPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance(null,
				"test-svc-1", "1.1.1.1", 80,
				Map.of("port.tcp1", "80", "port.tcp2", "443", "k8s_namespace", "namespace1", "type", "ClusterIP"),
				false, "namespace1", null));
	}

	@Test
	void getInstancesShouldReturnInstancesWithTheSameServiceIdFromNamespaces() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1, ENDPOINTS_2);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(
				new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
						Map.of("port.<unset>", "8080", "k8s_namespace", "namespace1", "type", "ClusterIP"), false,
						"namespace1", null),
				new DefaultKubernetesServiceInstance(null, "test-svc-1", "2.2.2.2", 8080,
						Map.of("port.<unset>", "8080", "k8s_namespace", "namespace2", "type", "ClusterIP"), false,
						"namespace2", null));
	}

	@Test
	void testBothServicesMatchesFilter() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1, ENDPOINTS_3);

		String spelFilter = """
				#root.metadata.namespace matches "^.+1$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, properties);

		assertThat(discoveryClient.getServices()).contains("test-svc-1", "test-svc-3");

		List<ServiceInstance> one = discoveryClient.getInstances("test-svc-1");
		assertThat(one.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespace1");

		List<ServiceInstance> two = discoveryClient.getInstances("test-svc-3");
		assertThat(two.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespace1");

	}

	@Test
	void testOneServiceMatchesFilter() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1, ENDPOINTS_2);

		// without filter, both match
		String spelFilter = "";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, properties);

		// only one here because of distinct
		assertThat(discoveryClient.getServices()).contains("test-svc-1");

		List<ServiceInstance> result = discoveryClient.getInstances("test-svc-1").stream()
				.sorted(Comparator.comparing(res -> res.getMetadata().get("k8s_namespace"))).toList();
		assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespace1");
		assertThat(result.get(1).getMetadata().get("k8s_namespace")).isEqualTo("namespace2");

		// with filter, only one matches

		spelFilter = """
				#root.metadata.namespace matches "^.+1$"
				""";
		properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L, false, spelFilter, Set.of(),
				Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);
		discoveryClient = new KubernetesInformerDiscoveryClient(SHARED_INFORMER_FACTORY, serviceLister, endpointsLister,
				null, null, properties);

		// only one here because of distinct
		assertThat(discoveryClient.getServices()).contains("test-svc-1");

		result = discoveryClient.getInstances("test-svc-1").stream()
				.sorted(Comparator.comparing(res -> res.getMetadata().get("k8s_namespace"))).toList();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespace1");

	}

	@Test
	void testServicesWithDifferentMetadataLabels() {
		V1Service serviceA = service("serviceX", "namespaceA", Map.of("shape", "round"));
		V1Service serviceB = service("serviceX", "namespaceB", Map.of("shape", "triangle"));

		V1Endpoints endpointsA = endpointsReadyAddress("serviceX", "namespaceA");
		V1Endpoints endpointsB = endpointsReadyAddress("serviceX", "namespaceB");

		Lister<V1Service> serviceLister = setupServiceLister(serviceA, serviceB);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(endpointsA, endpointsB);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, true, Set.of(), true, 60L,
				false, null, Set.of(), Map.of("shape", "round"), null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
				0, false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, properties);

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceX");
		assertThat(serviceInstances.size()).isEqualTo(1);
		assertThat(serviceInstances.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
	}

	@Test
	void testServicesWithSameMetadataLabels() {
		V1Service serviceA = service("serviceX", "namespaceA", Map.of("shape", "round"));
		V1Service serviceB = service("serviceX", "namespaceB", Map.of("shape", "round"));

		V1Endpoints endpointsA = endpointsReadyAddress("serviceX", "namespaceA");
		V1Endpoints endpointsB = endpointsReadyAddress("serviceX", "namespaceB");

		Lister<V1Service> serviceLister = setupServiceLister(serviceA, serviceB);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(endpointsA, endpointsB);

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, true, Set.of(), true, 60L,
				false, null, Set.of(), Map.of("shape", "round"), null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
				0, false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, properties);

		List<ServiceInstance> serviceInstances = discoveryClient.getInstances("serviceX").stream()
				.sorted(Comparator.comparing(x -> x.getMetadata().get("k8s_namespace"))).toList();
		assertThat(serviceInstances.size()).isEqualTo(2);
		assertThat(serviceInstances.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespaceA");
		assertThat(serviceInstances.get(1).getMetadata().get("k8s_namespace")).isEqualTo("namespaceB");
	}

	@Test
	void testExternalNameService() {
		V1Service externalNameService = new V1ServiceBuilder()
				.withSpec(new V1ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring-b").build())
				.withNewMetadata().withLabels(Map.of("label-key", "label-value")).withAnnotations(Map.of("abc", "def"))
				.withName("blue-service").withNamespace("b").endMetadata().build();

		V1Endpoints endpoints = new V1EndpointsBuilder().withMetadata(new V1ObjectMeta().namespace("irrelevant"))
				.build();

		Lister<V1Service> serviceLister = setupServiceLister(externalNameService);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(endpoints);

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true,
				"labels-prefix-", true, "annotations-prefix-", true, "ports-prefix");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", metadata, 0, false, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, properties);

		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertEquals(result.size(), 1);
		DefaultKubernetesServiceInstance externalNameServiceInstance = (DefaultKubernetesServiceInstance) result.get(0);
		Assertions.assertEquals(externalNameServiceInstance.getServiceId(), "blue-service");
		Assertions.assertEquals(externalNameServiceInstance.getHost(), "k8s-spring-b");
		Assertions.assertEquals(externalNameServiceInstance.getPort(), -1);
		Assertions.assertFalse(externalNameServiceInstance.isSecure());
		Assertions.assertEquals(externalNameServiceInstance.getUri().toASCIIString(), "k8s-spring-b");
		Assertions.assertEquals(externalNameServiceInstance.getMetadata(), Map.of("k8s_namespace", "b",
				"labels-prefix-label-key", "label-value", "annotations-prefix-abc", "def", "type", "ExternalName"));
	}

	@Test
	void testPodMetadata() {
		V1Service nonExternalNameService = new V1ServiceBuilder()
				.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP").build()).withNewMetadata()
				.withName("blue-service").withNamespace("a").endMetadata().build();

		V1Endpoints endpoints = new V1EndpointsBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName("blue-service").withNamespace("a").build())
				.withSubsets(
						new V1EndpointSubsetBuilder().withPorts(new CoreV1EndpointPortBuilder().withPort(8080).build())
								.withAddresses(new V1EndpointAddressBuilder().withIp("127.0.0.1").withTargetRef(
										new V1ObjectReferenceBuilder().withKind("Pod").withName("my-pod").build())
										.build())
								.build())
				.build();

		Lister<V1Service> serviceLister = setupServiceLister(nonExternalNameService);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(endpoints);

		WireMockServer server = new WireMockServer(options().dynamicPort());
		server.start();
		WireMock.configureFor("localhost", server.port());
		ApiClient apiClient = new ClientBuilder().setBasePath("http://localhost:" + server.port()).build();

		V1Pod pod = new V1PodBuilder().withNewMetadata().withName("my-pod").withLabels(Map.of("a", "b"))
				.withAnnotations(Map.of("c", "d")).endMetadata().build();

		WireMock.stubFor(WireMock.get("/api/v1/namespaces/a/pods/my-pod")
				.willReturn(WireMock.aResponse().withStatus(200).withBody(new JSON().serialize(pod))));

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true,
				"labels-prefix-", true, "annotations-prefix-", true, "ports-prefix", true, true);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("a", "b"),
				true, 60L, false, "", Set.of(), Map.of(), "", metadata, 0, false, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(
				SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, properties);
		discoveryClient.coreV1Api = new CoreV1Api(apiClient);

		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertEquals(result.size(), 1);
		DefaultKubernetesServiceInstance serviceInstance = (DefaultKubernetesServiceInstance) result.get(0);
		Assertions.assertEquals(serviceInstance.getServiceId(), "blue-service");
		Assertions.assertEquals(serviceInstance.getHost(), "127.0.0.1");
		Assertions.assertEquals(serviceInstance.getPort(), 8080);
		Assertions.assertFalse(serviceInstance.isSecure());
		Assertions.assertEquals(serviceInstance.getUri().toASCIIString(), "http://127.0.0.1:8080");
		Assertions.assertEquals(serviceInstance.getMetadata(),
				Map.of("k8s_namespace", "a", "type", "ClusterIP", "ports-prefix<unset>", "8080"));
		Assertions.assertEquals(serviceInstance.podMetadata().get("labels"), Map.of("a", "b"));
		Assertions.assertEquals(serviceInstance.podMetadata().get("annotations"), Map.of("c", "d"));

		server.shutdown();
	}

	private Lister<V1Service> setupServiceLister(V1Service... services) {
		Cache<V1Service> serviceCache = new Cache<>();
		Lister<V1Service> serviceLister = new Lister<>(serviceCache);
		for (V1Service svc : services) {
			serviceCache.add(svc);
		}
		return serviceLister;
	}

	private Lister<V1Endpoints> setupEndpointsLister(V1Endpoints... endpoints) {
		Cache<V1Endpoints> endpointsCache = new Cache<>();
		Lister<V1Endpoints> endpointsLister = new Lister<>(endpointsCache);
		for (V1Endpoints ep : endpoints) {
			endpointsCache.add(ep);
		}
		return endpointsLister;
	}

	private static V1Service service(String name, String namespace, Map<String, String> labels) {
		return new V1Service().metadata(new V1ObjectMeta().name(name).namespace(namespace).labels(labels))
				.spec(new V1ServiceSpec().type("ClusterIP"));
	}

	private static V1Endpoints endpointsReadyAddress(String name, String namespace) {
		return new V1Endpoints().metadata(new V1ObjectMeta().name(name).namespace(namespace))
				.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
						.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));
	}

	private static V1Endpoints endpointsNotReadyAddress() {
		return new V1Endpoints().metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
				.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
						.addNotReadyAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));
	}

	private static V1Endpoints endpointsNoPorts() {
		return new V1Endpoints().metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
				.addSubsetsItem(new V1EndpointSubset().addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
	}

	private static V1Endpoints endpointsNoUnsetPortName() {
		return new V1Endpoints().metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
				.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(80))
						.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
	}

	private static V1Endpoints endpointsWithMultiplePorts() {
		return new V1Endpoints().metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
				.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().name("http").port(80))
						.addPortsItem(new CoreV1EndpointPort().name("https").port(443))
						.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
	}

	private static V1Endpoints endpointsWithMultiplePortsNoHttps() {
		return new V1Endpoints().metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
				.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().name("http").port(80))
						.addPortsItem(new CoreV1EndpointPort().name("tcp").port(443))
						.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
	}

	private static V1Endpoints endpointsMultiplePortsWithoutSupportedPortNames() {
		return new V1Endpoints().metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
				.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().name("tcp1").port(80))
						.addPortsItem(new CoreV1EndpointPort().name("tcp2").port(443))
						.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));
	}

	private static KubernetesDiscoveryProperties properties(boolean allNamespaces, Map<String, String> labels) {
		return new KubernetesDiscoveryProperties(false, allNamespaces, Set.of(), true, 60, false, null, Set.of(),
				labels, null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
	}

}
