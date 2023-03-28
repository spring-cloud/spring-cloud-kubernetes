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
import reactor.test.StepVerifier;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.mock.env.MockEnvironment;

import static io.kubernetes.client.util.Namespaces.NAMESPACE_ALL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author Ryan Baxter
 */
class KubernetesInformerReactiveDiscoveryClientTests {

	private static final Cache<V1Service> SERVICE_CACHE = new Cache<>();

	private static final Cache<V1Endpoints> ENDPOINTS_CACHE = new Cache<>();

	private static final String NAMESPACE_1 = "namespace1";

	private static final String NAMESPACE_2 = "namespace2";

	private final SharedInformerFactory sharedInformerFactory = Mockito.mock(SharedInformerFactory.class);

	private static final V1Service TEST_SERVICE_1 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE_1))
			.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus());

	private static final V1Service TEST_SERVICE_2 = new V1Service()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE_2));

	private static final V1Endpoints TEST_ENDPOINTS_1 = new V1Endpoints()
			.metadata(new V1ObjectMeta().name("test-svc-1").namespace(NAMESPACE_1))
			.addSubsetsItem(new V1EndpointSubset().addPortsItem(new CoreV1EndpointPort().port(8080))
					.addAddressesItem(new V1EndpointAddress().ip("2.2.2.2")));

	@Test
	void testDiscoveryGetServicesAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_ALL, TEST_SERVICE_1, TEST_SERVICE_2);

		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				new KubernetesNamespaceProvider(new MockEnvironment()), sharedInformerFactory, serviceLister, null,
				null, null, KubernetesDiscoveryProperties.DEFAULT);

		StepVerifier.create(discoveryClient.getServices())
				.expectNext(TEST_SERVICE_1.getMetadata().getName(), TEST_SERVICE_2.getMetadata().getName()).expectComplete()
				.verify();

	}

	@Test
	void testDiscoveryGetServicesOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_1, TEST_SERVICE_1, TEST_SERVICE_2);

		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn(NAMESPACE_1);
		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				kubernetesNamespaceProvider, sharedInformerFactory, serviceLister, null, null, null,
				KubernetesDiscoveryProperties.DEFAULT);

		StepVerifier.create(discoveryClient.getServices()).expectNext(TEST_SERVICE_1.getMetadata().getName())
				.expectComplete().verify();

	}

	@Test
	void testDiscoveryGetInstanceAllNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_ALL, TEST_SERVICE_1, TEST_SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(TEST_ENDPOINTS_1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, true,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false);

		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				new KubernetesNamespaceProvider(new MockEnvironment()), sharedInformerFactory, serviceLister,
				endpointsLister, null, null, kubernetesDiscoveryProperties);

		StepVerifier
				.create(discoveryClient.getInstances("test-svc-1")).expectNext(new DefaultKubernetesServiceInstance("",
						"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, NAMESPACE_1, null))
				.expectComplete().verify();

	}

	@Test
	void testDiscoveryGetInstanceOneNamespaceShouldWork() {
		Lister<V1Service> serviceLister = setupServiceLister(NAMESPACE_1, TEST_SERVICE_1, TEST_SERVICE_2);
		Lister<V1Endpoints> endpointsLister = setupEndpointsLister(TEST_ENDPOINTS_1);

		KubernetesDiscoveryProperties kubernetesDiscoveryProperties = new KubernetesDiscoveryProperties(true, false,
				Set.of(), true, 60, false, null, Set.of(), Map.of(), null, null, 0, false);

		KubernetesNamespaceProvider kubernetesNamespaceProvider = mock(KubernetesNamespaceProvider.class);
		when(kubernetesNamespaceProvider.getNamespace()).thenReturn(NAMESPACE_1);
		KubernetesInformerReactiveDiscoveryClient discoveryClient = new KubernetesInformerReactiveDiscoveryClient(
				kubernetesNamespaceProvider, sharedInformerFactory, serviceLister, endpointsLister, null, null,
				kubernetesDiscoveryProperties);

		StepVerifier
				.create(discoveryClient.getInstances("test-svc-1")).expectNext(new DefaultKubernetesServiceInstance("",
						"test-svc-1", "2.2.2.2", 8080, new HashMap<>(), false, NAMESPACE_1, null))
				.expectComplete().verify();

	}

	private Lister<V1Service> setupServiceLister(String namespace, V1Service... services) {
		Lister<V1Service> serviceLister = new Lister<>(SERVICE_CACHE).namespace(namespace);
		for (V1Service svc : services) {
			SERVICE_CACHE.add(svc);
		}
		return serviceLister;
	}

	private Lister<V1Endpoints> setupEndpointsLister(V1Endpoints... endpoints) {
		Lister<V1Endpoints> endpointsLister = new Lister<>(ENDPOINTS_CACHE);
		for (V1Endpoints ep : endpoints) {
			ENDPOINTS_CACHE.add(ep);
		}
		return endpointsLister;
	}

}
