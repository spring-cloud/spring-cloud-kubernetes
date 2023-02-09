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

import java.util.Map;
import java.util.Set;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

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

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray())
			.containsOnly(new DefaultKubernetesServiceInstance("", "test-svc-1", "1.1.1.1", 80,
				Map.of("<unset>", "80"), false, "namespace1", null));
	}

	@Test
	void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, null, null, null, KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_1.getMetadata().getName(),
			SERVICE_2.getMetadata().getName());

	}

	@Test
	void testDiscoveryWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2, SERVICE_3);

		Map<String, String> labels = Map.of("k8s", "true", "spring", "true");
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = properties(true, labels);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_3.getMetadata().getName());

	}

	@Test
	void testDiscoveryInstancesWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2, SERVICE_3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1, ENDPOINTS_3);

		Map<String, String> labels = Map.of("k8s", "true", "spring", "true");
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = properties(true, labels);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray()).isEmpty();
		assertThat(discoveryClient.getInstances("test-svc-3").toArray())
			.containsOnly(new DefaultKubernetesServiceInstance("", "test-svc-3", "2.2.2.2", 8080,
				Map.of("spring", "true", "<unset>", "8080", "k8s", "true"), false, "namespace1", null));
	}

	@Test
	void testDiscoveryInstancesWithSecuredServiceByAnnotations() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_4);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);
		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_4.getMetadata().getName());
		ServiceInstance serviceInstance = discoveryClient.getInstances(SERVICE_4.getMetadata().getName()).get(0);
		assertThat(serviceInstance.isSecure()).isTrue();
	}

	@Test
	void testDiscoveryInstancesWithSecuredServiceByLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_4);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_4.getMetadata().getName());
		ServiceInstance serviceInstance = discoveryClient.getInstances(SERVICE_4.getMetadata().getName()).get(0);
		assertThat(serviceInstance.isSecure()).isTrue();
	}

	@Test
	void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, null, null, null, KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_1.getMetadata().getName());

	}

	@Test
	void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "2.2.2.2", 8080, Map.of("<unset>", "8080"), false, "namespace1", null));
	}

	@Test
	void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "2.2.2.2", 8080, Map.of("<unset>", "8080"), false, "namespace1", null));
	}

	@Test
	void testDiscoveryGetInstanceWithoutReadyAddressesShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NOT_READY_ADDRESS);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
			KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void testDiscoveryGetInstanceWithNotReadyAddressesIncludedShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NOT_READY_ADDRESS);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
			Set.of(), true, 60, true, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "2.2.2.2", 8080, Map.of(), false, "namespace1", null));
	}

	@Test
	void instanceWithoutEndpointsShouldBeSkipped() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
			KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void instanceWithoutPortsShouldBeSkipped() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_NO_PORTS);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
			KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void instanceWithMultiplePortsAndPrimaryPortNameConfiguredWithLabelShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_6);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1"))
			.containsOnly(new DefaultKubernetesServiceInstance("", "test-svc-1", "1.1.1.1", 443,
				Map.of("http", "80", "primary-port-name", "https", "https", "443"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_5);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
			ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1"))
			.containsOnly(new DefaultKubernetesServiceInstance("", "test-svc-1", "1.1.1.1", 80,
				Map.of("tcp1", "80", "primary-port-name", "oops", "tcp2", "443"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndGenericPrimaryPortNameConfiguredShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "1.1.1.1", 443, Map.of("http", "80", "https", "443"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
			ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "1.1.1.1", 80, Map.of("tcp1", "80", "tcp2", "443"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttpsPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "1.1.1.1", 443, Map.of("http", "80", "https", "443"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttpPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_WITH_MULTIPLE_PORTS_NO_HTTPS);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "1.1.1.1", 80, Map.of("http", "80", "tcp", "443"), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutAnyConfigurationShouldPickTheFirstPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
			ENDPOINTS_MULTIPLE_PORTS_WITHOUT_SUPPORTED_PORT_NAMES);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, NOT_ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
			"test-svc-1", "1.1.1.1", 80, Map.of("tcp1", "80", "tcp2", "443"), false, "namespace1", null));
	}

	@Test
	void getInstancesShouldReturnInstancesWithTheSameServiceIdFromNamespaces() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(ENDPOINTS_1, ENDPOINTS_2);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(null,
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, ALL_NAMESPACES);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(
			new DefaultKubernetesServiceInstance("", "test-svc-1", "2.2.2.2", 8080, Map.of("<unset>", "8080"),
				false, "namespace1", null),
			new DefaultKubernetesServiceInstance("", "test-svc-1", "2.2.2.2", 8080, Map.of("<unset>", "8080"),
				false, "namespace2", null));
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
		return new V1Service().metadata(new V1ObjectMeta().name(name).namespace(namespace).labels(labels));
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

