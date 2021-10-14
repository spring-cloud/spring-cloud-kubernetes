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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class DiscoveryServerControllerTests {

	private static final KubernetesServiceInstance serviceAInstance1 = new KubernetesServiceInstance("serviceAInstance1",
		"serviceAInstance1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null);

	private static final KubernetesServiceInstance serviceAInstance2 = new KubernetesServiceInstance("serviceAInstance2",
		"serviceAInstance2", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null);

	private static final KubernetesServiceInstance serviceAInstance3 = new KubernetesServiceInstance("serviceAInstance3",
		"serviceAInstance3", "2.2.2.2", 8080, new HashMap<>(), false, "namespace2", null);

	private static final KubernetesServiceInstance serviceBInstance1 = new KubernetesServiceInstance("serviceBInstance1",
		"serviceBInstance1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace1", null);

	private static final KubernetesServiceInstance serviceCInstance1 = new KubernetesServiceInstance("serviceCInstance1",
		"serviceCInstance1", "2.2.2.2", 8080, new HashMap<>(), false, "namespace2", null);

	private static DiscoveryServerController.Service serviceA = new DiscoveryServerController.Service();
	private static DiscoveryServerController.Service serviceB = new DiscoveryServerController.Service();
	private static DiscoveryServerController.Service serviceC = new DiscoveryServerController.Service();

	private static KubernetesInformerReactiveDiscoveryClient discoveryClient;

	@BeforeAll
	static void beforeAll() {
		Flux<String> services = Flux.just("serviceA", "serviceB", "serviceC");

		List<ServiceInstance> serviceAInstanceList = new ArrayList<>();
		serviceAInstanceList.add(serviceAInstance1);
		serviceAInstanceList.add(serviceAInstance2);
		serviceAInstanceList.add(serviceAInstance3);

		Flux<ServiceInstance> serviceAInstances = Flux.fromIterable(serviceAInstanceList);

		List<ServiceInstance> serviceBInstanceList = Collections.singletonList(serviceBInstance1);
		Flux<ServiceInstance> serviceBInstances = Flux.fromIterable(serviceBInstanceList);

		List<ServiceInstance> serviceCInstanceList = Collections.singletonList(serviceCInstance1);
		Flux<ServiceInstance> serviceCInstances = Flux.fromIterable(serviceCInstanceList);

		discoveryClient = mock(KubernetesInformerReactiveDiscoveryClient.class);
		when(discoveryClient.getServices()).thenReturn(services);
		when(discoveryClient.getInstances(eq("serviceA"))).thenReturn(serviceAInstances);
		when(discoveryClient.getInstances(eq("serviceB"))).thenReturn(serviceBInstances);
		when(discoveryClient.getInstances(eq("serviceC"))).thenReturn(serviceCInstances);
		when(discoveryClient.getInstances(eq("serviceD"))).thenReturn(Flux.empty());

		serviceA.setName("serviceA");
		serviceA.setServiceInstances(serviceAInstanceList);

		serviceB.setName("serviceB");
		serviceB.setServiceInstances(serviceBInstanceList);

		serviceC.setName("serviceC");
		serviceC.setServiceInstances(serviceCInstanceList);
	}



	@Test
	void apps() {
		DiscoveryServerController controller = new DiscoveryServerController(discoveryClient);
		StepVerifier.create(controller.apps()).expectNext(serviceA, serviceB, serviceC).verifyComplete();
	}

	@Test
	void appInstances() {
		DiscoveryServerController controller = new DiscoveryServerController(discoveryClient);
		StepVerifier.create(controller.appInstances("serviceA")).expectNext(serviceAInstance1, serviceAInstance2, serviceAInstance3).verifyComplete();
		StepVerifier.create(controller.appInstances("serviceB")).expectNext(serviceBInstance1).verifyComplete();
		StepVerifier.create(controller.appInstances("serviceC")).expectNext(serviceCInstance1).verifyComplete();
		StepVerifier.create(controller.appInstances("serviceD")).expectNextCount(0).verifyComplete();
	}

	@Test
	void appInstance() {
		DiscoveryServerController controller = new DiscoveryServerController(discoveryClient);
		StepVerifier.create(controller.appInstance("serviceA", "serviceAInstance2")).expectNext(serviceAInstance2).verifyComplete();
		StepVerifier.create(controller.appInstance("serviceB", "doesnotexist")).expectNextCount(0).verifyComplete();
	}
}
