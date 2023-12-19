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

package org.springframework.cloud.kubernetes.discoveryserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.client.discovery.reactive.KubernetesInformerReactiveDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.Service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class DiscoveryServerControllerTests {

	private static final DefaultKubernetesServiceInstance SERVICE_A_INSTANCE_1 = new DefaultKubernetesServiceInstance(
			"serviceAInstance1", "serviceAInstance1", "2.2.2.2", 8080, Map.of(), false, "namespace1", null);

	private static final DefaultKubernetesServiceInstance SERVICE_A_INSTANCE_2 = new DefaultKubernetesServiceInstance(
			"serviceAInstance2", "serviceAInstance2", "2.2.2.2", 8080, Map.of(), false, "namespace1", null);

	private static final DefaultKubernetesServiceInstance SERVICE_A_INSTANCE_3 = new DefaultKubernetesServiceInstance(
			"serviceAInstance3", "serviceAInstance3", "2.2.2.2", 8080, Map.of(), false, "namespace2", null);

	private static final DefaultKubernetesServiceInstance SERVICE_B_INSTANCE_1 = new DefaultKubernetesServiceInstance(
			"serviceBInstance1", "serviceBInstance1", "2.2.2.2", 8080, Map.of(), false, "namespace1", null);

	private static final DefaultKubernetesServiceInstance SERVICE_C_INSTANCE_1 = new DefaultKubernetesServiceInstance(
			"serviceCInstance1", "serviceCInstance1", "2.2.2.2", 8080, Map.of(), false, "namespace2", null);

	private static Service serviceA;

	private static Service serviceB;

	private static Service serviceC;

	private static KubernetesInformerReactiveDiscoveryClient discoveryClient;

	@BeforeAll
	static void beforeAll() {
		Flux<String> services = Flux.just("serviceA", "serviceB", "serviceC");

		List<DefaultKubernetesServiceInstance> serviceAInstanceList = new ArrayList<>();
		serviceAInstanceList.add(SERVICE_A_INSTANCE_1);
		serviceAInstanceList.add(SERVICE_A_INSTANCE_2);
		serviceAInstanceList.add(SERVICE_A_INSTANCE_3);

		Flux<ServiceInstance> serviceAInstances = Flux.fromIterable(serviceAInstanceList);

		List<DefaultKubernetesServiceInstance> serviceBInstanceList = Collections.singletonList(SERVICE_B_INSTANCE_1);
		Flux<ServiceInstance> serviceBInstances = Flux.fromIterable(serviceBInstanceList);

		List<DefaultKubernetesServiceInstance> serviceCInstanceList = Collections.singletonList(SERVICE_C_INSTANCE_1);
		Flux<ServiceInstance> serviceCInstances = Flux.fromIterable(serviceCInstanceList);

		discoveryClient = mock(KubernetesInformerReactiveDiscoveryClient.class);
		when(discoveryClient.getServices()).thenReturn(services);
		when(discoveryClient.getInstances(eq("serviceA"))).thenReturn(serviceAInstances);
		when(discoveryClient.getInstances(eq("serviceB"))).thenReturn(serviceBInstances);
		when(discoveryClient.getInstances(eq("serviceC"))).thenReturn(serviceCInstances);
		when(discoveryClient.getInstances(eq("serviceD"))).thenReturn(Flux.empty());

		serviceA = new Service("serviceA", serviceAInstanceList);
		serviceB = new Service("serviceB", serviceBInstanceList);
		serviceC = new Service("serviceC", serviceCInstanceList);

	}

	@Test
	void apps() {
		DiscoveryServerController controller = new DiscoveryServerController(discoveryClient);
		StepVerifier.create(controller.apps()).expectNext(serviceA, serviceB, serviceC).verifyComplete();
	}

	@Test
	void appInstances() {
		DiscoveryServerController controller = new DiscoveryServerController(discoveryClient);
		StepVerifier.create(controller.appInstances("serviceA"))
				.expectNext(SERVICE_A_INSTANCE_1, SERVICE_A_INSTANCE_2, SERVICE_A_INSTANCE_3).verifyComplete();
		StepVerifier.create(controller.appInstances("serviceB")).expectNext(SERVICE_B_INSTANCE_1).verifyComplete();
		StepVerifier.create(controller.appInstances("serviceC")).expectNext(SERVICE_C_INSTANCE_1).verifyComplete();
		StepVerifier.create(controller.appInstances("serviceD")).expectNextCount(0).verifyComplete();
	}

	@Test
	void appInstance() {
		DiscoveryServerController controller = new DiscoveryServerController(discoveryClient);
		StepVerifier.create(controller.appInstance("serviceA", "serviceAInstance2")).expectNext(SERVICE_A_INSTANCE_2)
				.verifyComplete();
		StepVerifier.create(controller.appInstance("serviceB", "doesnotexist")).expectNextCount(0).verifyComplete();
	}

}
