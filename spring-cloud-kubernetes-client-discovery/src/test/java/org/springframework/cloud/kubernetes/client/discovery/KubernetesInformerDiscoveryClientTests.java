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
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesInformerDiscoveryClientTests {

	private static final SharedInformerFactory SHARED_INFORMER_FACTORY = Mockito.mock(SharedInformerFactory.class);

	private static final V1Service SERVICE_1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"));

	private static final V1Service SERVICE_2 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"));

	private static final V1Service SERVICE_3 = new V1Service()
		.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1").labels(Map.of("spring", "true", "k8s", "true")));

	private static final V1Service testServiceSecuredAnnotation1 = new V1Service()
			.metadata(
					new V1ObjectMeta().name("test-svc-1").namespace("namespace1").labels(Map.of("secured", "true")))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service testServiceSecuredLabel1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1").putLabelsItem("secured", "true"));

	private static final V1Endpoints testEndpoints1 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	private static final V1Endpoints testEndpoints2 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	private static final V1Endpoints testEndpointWithoutReadyAddresses = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
					.addNotReadyAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	private static final V1Endpoints testEndpointWithoutPorts = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithUnsetPortName = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(80))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithMultiplePorts = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().name("http").port(80))
					.addPortsItem(new CoreV1EndpointPort().name("https").port(443))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithMultiplePortsWithoutHttps = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().name("http").port(80))
					.addPortsItem(new CoreV1EndpointPort().name("tcp").port(443))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithMultiplePortsWithoutSupportedPortNames = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().name("tcp1").port(80))
					.addPortsItem(new CoreV1EndpointPort().name("tcp2").port(443))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpoints3 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	@Test
	void testServiceWithUnsetPortNames() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithUnsetPortName);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null,
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray())
				.containsOnly(new DefaultKubernetesServiceInstance("", "test-svc-1", "1.1.1.1", 80, Map.of("<unset>", "80"), false,
						"namespace1", null));
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

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), labels, null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(SERVICE_3.getMetadata().getName());

	}

	@Test
	void testDiscoveryInstancesWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2, SERVICE_3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1, testEndpoints3);

		Map<String, String> labels = Map.of("k8s", "true", "spring", "true");

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), labels, null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray()).isEmpty();
		assertThat(discoveryClient.getInstances("test-svc-3").toArray())
				.containsOnly(new DefaultKubernetesServiceInstance("", "test-svc-3", "2.2.2.2", 8080, Map.of(),
						false, "namespace1", null));
	}

	@Test
	void testDiscoveryInstancesWithSecuredServiceByAnnotations() {
		Lister<V1Service> serviceLister = setupServiceLister(testServiceSecuredAnnotation1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);
		assertThat(discoveryClient.getServices().toArray())
				.containsOnly(testServiceSecuredAnnotation1.getMetadata().getName());
		ServiceInstance serviceInstance = discoveryClient
				.getInstances(testServiceSecuredAnnotation1.getMetadata().getName()).get(0);
		assertThat(serviceInstance.isSecure()).isTrue();
	}

	@Test
	void testDiscoveryInstancesWithSecuredServiceByLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(testServiceSecuredLabel1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);
		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false);
		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray())
				.containsOnly(testServiceSecuredLabel1.getMetadata().getName());
		ServiceInstance serviceInstance = discoveryClient.getInstances(testServiceSecuredLabel1.getMetadata().getName())
				.get(0);
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
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "2.2.2.2", 8080, Map.of(), false, "namespace1", null));
	}

	@Test
	void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "2.2.2.2", 8080, Map.of(), false, "namespace1", null));
	}

	@Test
	void testDiscoveryGetInstanceWithoutReadyAddressesShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithoutReadyAddresses);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void testDiscoveryGetInstanceWithNotReadyAddressesIncludedShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithoutReadyAddresses);

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
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithoutPorts);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
	}

	@Test
	void instanceWithMultiplePortsAndPrimaryPortNameConfiguredWithLabelShouldWork() {
		V1ObjectMeta oldMetadata = SERVICE_1.getMetadata();
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1.metadata(new V1ObjectMeta().name("test-svc-1")
				.namespace("namespace1").putLabelsItem("primary-port-name", "https")));
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePorts);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 443, Map.of(), false, "namespace1", null));
		SERVICE_1.metadata(oldMetadata);
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelShouldReturnFirstPortAndLogWarning() {
		V1ObjectMeta oldMetadata = SERVICE_1.getMetadata();
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1.metadata(new V1ObjectMeta().name("test-svc-1")
				.namespace("namespace1").putLabelsItem("primary-port-name", "oops")));
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				testEndpointWithMultiplePortsWithoutSupportedPortNames);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, Map.of(), false, "namespace1", null));
		SERVICE_1.metadata(oldMetadata);
	}

	@Test
	void instanceWithMultiplePortsAndGenericPrimaryPortNameConfiguredShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePorts);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), "https", null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 443, Map.of(), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				testEndpointWithMultiplePortsWithoutSupportedPortNames);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), "oops", null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, Map.of(), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttpsPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePorts);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 443, Map.of(), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttpPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePortsWithoutHttps);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, Map.of(), false, "namespace1", null));
	}

	@Test
	void instanceWithMultiplePortsAndWithoutAnyConfigurationShouldPickTheFirstPort() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				testEndpointWithMultiplePortsWithoutSupportedPortNames);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new DefaultKubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, Map.of(), false, "namespace1", null));
	}

	@Test
	void getInstancesShouldReturnInstancesWithTheSameServiceIdFromNamespaces() {
		Lister<V1Service> serviceLister = setupServiceLister(SERVICE_1, SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1, testEndpoints2);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient(null,
			SHARED_INFORMER_FACTORY, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(
				new DefaultKubernetesServiceInstance("", "test-svc-1", "2.2.2.2", 8080, Map.of(), false,
						"namespace1", null),
				new DefaultKubernetesServiceInstance("", "test-svc-1", "2.2.2.2", 8080, Map.of(), false,
						"namespace2", null));
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

}
