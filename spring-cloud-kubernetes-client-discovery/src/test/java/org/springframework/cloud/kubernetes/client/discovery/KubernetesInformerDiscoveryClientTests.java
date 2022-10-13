/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesInformerDiscoveryClientTests {

	@Mock
	private SharedInformerFactory sharedInformerFactory;

	@Mock
	private KubernetesDiscoveryProperties kubernetesDiscoveryProperties;

	private static final V1Service testService1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service testService2 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service testService3 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1").putLabelsItem("spring", "true")
					.putLabelsItem("k8s", "true"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints testEndpoints1 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	private static final V1Endpoints testEndpointWithoutReadyAddresses = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080))
					.addNotReadyAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	private static final V1Endpoints testEndpointWithoutPorts = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithUnsetPortName = new V1Endpoints()
		.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
		.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(80))
			.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithMultiplePorts = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().name("http").port(80))
					.addPortsItem(new V1EndpointPort().name("https").port(443))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithMultiplePortsWithoutHttps = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().name("http").port(80))
					.addPortsItem(new V1EndpointPort().name("tcp").port(443))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpointWithMultiplePortsWithoutSupportedPortNames = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().name("tcp1").port(80))
					.addPortsItem(new V1EndpointPort().name("tcp2").port(443))
					.addAddressesItem(new V1EndpointAddress().ip("1.1.1.1")));

	private static final V1Endpoints testEndpoints3 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-3").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	@Test
	public void testServiceWithUnsetPortNames() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithUnsetPortName);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(true);
		when(kubernetesDiscoveryProperties.getMetadata()).thenReturn(new KubernetesDiscoveryProperties.Metadata());

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
			sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		Map<String, String> ports = new HashMap<>();
		ports.put("<unset>", "80");
		assertThat(discoveryClient.getInstances("test-svc-1").toArray()).containsOnly(new KubernetesServiceInstance("",
			"test-svc-1", "1.1.1.1", 80, ports, false, "namespace1", null));
	}

	@Test
	public void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
				sharedInformerFactory, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(testService1.getMetadata().getName(),
				testService2.getMetadata().getName());

		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2, testService3);

		Map<String, String> labels = new HashMap<>();
		labels.put("k8s", "true");
		labels.put("spring", "true");

		when(kubernetesDiscoveryProperties.getServiceLabels()).thenReturn(labels);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
				sharedInformerFactory, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(testService3.getMetadata().getName());

		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryInstancesWithServiceLabels() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2, testService3);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1, testEndpoints3);

		Map<String, String> labels = new HashMap<>();
		labels.put("k8s", "true");
		labels.put("spring", "true");

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(true);
		when(kubernetesDiscoveryProperties.getServiceLabels()).thenReturn(labels);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1").toArray()).isEmpty();
		assertThat(discoveryClient.getInstances("test-svc-3").toArray()).containsOnly(new KubernetesServiceInstance("",
				"test-svc-3", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null));
	}

	@Test
	public void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, null, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getServices().toArray()).containsOnly(testService1.getMetadata().getName());

		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null));

		verify(kubernetesDiscoveryProperties, times(2)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
	}

	@Test
	public void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
	}

	@Test
	public void testDiscoveryGetInstanceWithoutReadyAddressesShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithoutReadyAddresses);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
		verify(kubernetesDiscoveryProperties, times(1)).isIncludeNotReadyAddresses();
	}

	@Test
	public void testDiscoveryGetInstanceWithNotReadyAddressesIncludedShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithoutReadyAddresses);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);
		when(kubernetesDiscoveryProperties.isIncludeNotReadyAddresses()).thenReturn(true);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
		verify(kubernetesDiscoveryProperties, times(1)).isIncludeNotReadyAddresses();
	}

	@Test
	public void instanceWithoutEndpointsShouldBeSkipped() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister();

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void instanceWithoutPortsShouldBeSkipped() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithoutPorts);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).isEmpty();
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
	}

	@Test
	public void instanceWithMultiplePortsAndPrimaryPortNameConfiguredWithLabelShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1.metadata(new V1ObjectMeta().name("test-svc-1")
				.namespace("namespace1").putLabelsItem("primary-port-name", "https")));
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePorts);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 443, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
		verify(kubernetesDiscoveryProperties, times(1)).isIncludeNotReadyAddresses();
	}

	@Test
	public void instanceWithMultiplePortsAndMisconfiguredPrimaryPortNameInLabelShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1.metadata(new V1ObjectMeta().name("test-svc-1")
				.namespace("namespace1").putLabelsItem("primary-port-name", "oops")));
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				testEndpointWithMultiplePortsWithoutSupportedPortNames);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
	}

	@Test
	public void instanceWithMultiplePortsAndGenericPrimaryPortNameConfiguredShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePorts);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);
		when(kubernetesDiscoveryProperties.getPrimaryPortName()).thenReturn("https");

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 443, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).isIncludeNotReadyAddresses();
	}

	@Test
	public void instanceWithMultiplePortsAndMisconfiguredGenericPrimaryPortNameShouldReturnFirstPortAndLogWarning() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				testEndpointWithMultiplePortsWithoutSupportedPortNames);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);
		when(kubernetesDiscoveryProperties.getPrimaryPortName()).thenReturn("oops");

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
	}

	@Test
	public void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedShouldFallBackToHttpsPort() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePorts);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 443, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
	}

	@Test
	public void instanceWithMultiplePortsAndWithoutPrimaryPortNameSpecifiedOrHttpsPortShouldFallBackToHttpPort() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpointWithMultiplePortsWithoutHttps);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
	}

	@Test
	public void instanceWithMultiplePortsAndWithoutAnyConfigurationShouldPickTheFirstPort() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(
				testEndpointWithMultiplePortsWithoutSupportedPortNames);

		when(kubernetesDiscoveryProperties.isAllNamespaces()).thenReturn(false);

		KubernetesInformerDiscoveryClient discoveryClient = new KubernetesInformerDiscoveryClient("namespace1",
				sharedInformerFactory, serviceLister, endpointsLister, null, null, kubernetesDiscoveryProperties);

		assertThat(discoveryClient.getInstances("test-svc-1")).containsOnly(new KubernetesServiceInstance("",
				"test-svc-1", "1.1.1.1", 80, new HashMap<>(), false, "namespace1", null));
		verify(kubernetesDiscoveryProperties, times(1)).isAllNamespaces();
		verify(kubernetesDiscoveryProperties, times(1)).getPrimaryPortName();
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
