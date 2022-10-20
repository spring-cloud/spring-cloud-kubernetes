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

package org.springframework.cloud.kubernetes.client.discovery.reactive;

import java.util.HashMap;
import java.util.Set;

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
import reactor.test.StepVerifier;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
@RunWith(MockitoJUnitRunner.class)
public class KubernetesInformerReactiveDiscoveryClientTests {

	@Mock
	private SharedInformerFactory sharedInformerFactory;

	private static final V1Service testService1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service testService2 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace2"))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Endpoints testEndpoints1 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace("namespace1"))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new V1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	@Test
	public void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);

		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				new KubernetesNamespaceProvider(new MockEnvironment()), sharedInformerFactory, serviceLister, null,
				null, null, KubernetesDiscoveryProperties.DEFAULT);

		StepVerifier.create(discoveryClient.getServices())
				.expectNext(testService1.getMetadata().getName(), testService2.getMetadata().getName()).expectComplete()
				.verify();

	}

	@Test
	public void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);

		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("namespace1");
		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				kubernetesNamespaceProvider, sharedInformerFactory, serviceLister, null, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		StepVerifier.create(discoveryClient.getServices()).expectNext(testService1.getMetadata().getName())
				.expectComplete().verify();

	}

	@Test
	public void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				true, 60, false, null, Set.of(), null, null, null, 0);

		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				new KubernetesNamespaceProvider(new MockEnvironment()), sharedInformerFactory, serviceLister,
				endpointsLister, null, null, kubernetesDiscoveryProperties);

		StepVerifier
				.create(discoveryClient.getInstances("test-svc-1")).expectNext(new DefaultKubernetesServiceInstance("",
						"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null))
				.expectComplete().verify();

	}

	@Test
	public void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(testService1, testService2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(testEndpoints1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				true, 60, false, null, Set.of(), null, null, null, 0);

		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn("namespace1");
		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				kubernetesNamespaceProvider, sharedInformerFactory, serviceLister, endpointsLister, null, null,
				kubernetesDiscoveryProperties);

		StepVerifier
				.create(discoveryClient.getInstances("test-svc-1")).expectNext(new DefaultKubernetesServiceInstance("",
						"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null))
				.expectComplete().verify();

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
