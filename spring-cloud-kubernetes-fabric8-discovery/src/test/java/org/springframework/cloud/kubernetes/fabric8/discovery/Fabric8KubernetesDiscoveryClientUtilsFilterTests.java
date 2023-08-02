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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.ALWAYS_TRUE;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesDiscoveryClientUtilsFilterTests {

	private static KubernetesClient client;

	private static final KubernetesDiscoveryProperties PROPERTIES = new KubernetesDiscoveryProperties(true, true,
			Set.of(), false, 60L, false, "some", Set.of(), Map.of(), "", null, 0, false);

	@AfterEach
	void afterEach() {
		client.endpoints().inAnyNamespace().delete();
		client.services().inAnyNamespace().delete();
	}

	@Test
	void withFilterEmptyInput() {
		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(), PROPERTIES, client,
				ALWAYS_TRUE);
		Assertions.assertEquals(result.size(), 0);
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Service with name "a" and namespace "namespace-not-a" present
	 *
	 *     As such, there is no match, empty result.
	 * </pre>
	 */
	@Test
	void withFilterOneEndpointsNoMatchInService() {
		Endpoints endpoints = createEndpoints("a", "namespace-a");
		createService("a", "namespace-not-a");
		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpoints), PROPERTIES,
				client, x -> true);
		Assertions.assertEquals(result.size(), 0);
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *
	 *     As such, there is a match.
	 * </pre>
	 */
	@Test
	void withFilterOneEndpointsMatchInService() {
		Endpoints endpoints = createEndpoints("a", "namespace-a");
		createService("a", "namespace-a");
		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpoints), PROPERTIES,
				client, ALWAYS_TRUE);
		Assertions.assertEquals(result.size(), 1);
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Endpoints with name : "b" and namespace "namespace-b", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *
	 *     As such, there is a match, single endpoints as result.
	 * </pre>
	 */
	@Test
	void withFilterTwoEndpointsOneMatchInService() {
		Endpoints endpointsA = createEndpoints("a", "namespace-a");
		Endpoints endpointsB = createEndpoints("b", "namespace-b");
		createService("a", "namespace-a");
		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpointsA, endpointsB),
				PROPERTIES, client, x -> true);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "a");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespace-a");
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Endpoints with name : "b" and namespace "namespace-b", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *     - Predicate that we use is "ALWAYS_TRUE", so no service filter is applied
	 *
	 *     As such, there is a match, single endpoints as result.
	 *     This test is the same as above with the difference in the predicate.
	 *     It simulates Fabric8EndpointsCatalogWatch::apply
	 * </pre>
	 */
	@Test
	void withFilterTwoEndpointsOneMatchInServiceAlwaysTruePredicate() {
		Endpoints endpointsA = createEndpoints("a", "namespace-a");
		Endpoints endpointsB = createEndpoints("b", "namespace-b");
		createService("a", "namespace-a");
		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpointsA, endpointsB),
				PROPERTIES, client, ALWAYS_TRUE);
		Assertions.assertEquals(result.size(), 2);
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Endpoints with name : "b" and namespace "namespace-b", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *     - Service with name "b" and namespace "namespace-b" present
	 *     - Service with name "c" and namespace "namespace-c" present
	 *
	 *     As such, there are two matches.
	 * </pre>
	 */
	@Test
	void withFilterTwoEndpointsAndThreeServices() {
		Endpoints endpointsA = createEndpoints("a", "namespace-a");
		Endpoints endpointsB = createEndpoints("b", "namespace-b");
		createService("a", "namespace-a");
		createService("b", "namespace-b");
		createService("c", "namespace-c");

		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpointsA, endpointsB),
				PROPERTIES, client, ALWAYS_TRUE);
		Assertions.assertEquals(result.size(), 2);
		result = result.stream().sorted(Comparator.comparing(x -> x.getMetadata().getName())).toList();
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "a");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespace-a");
		Assertions.assertEquals(result.get(1).getMetadata().getName(), "b");
		Assertions.assertEquals(result.get(1).getMetadata().getNamespace(), "namespace-b");
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Endpoints with name : "b" and namespace "namespace-b", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *     - Service with name "b" and namespace "namespace-b" present
	 *     - Service with name "c" and namespace "namespace-c" present
	 *
	 *     As such, there are two matches.
	 * </pre>
	 */
	@Test
	void withFilterSingleEndpointsMatchesFilter() {
		Endpoints endpointsA = createEndpoints("a", "namespace-a");
		createService("a", "namespace-a");

		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpointsA), PROPERTIES,
				client, x -> x.getMetadata().getNamespace().equals("namespace-a"));
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "a");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespace-a");
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a-1" and namespace "default", present
	 *     - Endpoints with name : "b-1" and namespace "default", present
	 *     - Endpoints with name : "c-2" and namespace "default", present
	 *     - Service with name "a-1" and namespace "default" present
	 *     - Service with name "b-1" and namespace "default" present
	 *
	 *     As such, there are two matches.
	 * </pre>
	 */
	@Test
	void withFilterTwoEndpointsMatchesFilter() {
		Endpoints endpointsA = createEndpoints("a-1", "default");
		Endpoints endpointsB = createEndpoints("b-1", "default");
		Endpoints endpointsC = createEndpoints("c-2", "default");
		createService("a-1", "default");
		createService("b-1", "default");

		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(
				List.of(endpointsA, endpointsB, endpointsC), PROPERTIES, client,
				x -> x.getMetadata().getName().contains("1"));
		Assertions.assertEquals(result.size(), 2);
		result = result.stream().sorted(Comparator.comparing(x -> x.getMetadata().getName())).toList();
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "a-1");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "default");
		Assertions.assertEquals(result.get(1).getMetadata().getName(), "b-1");
		Assertions.assertEquals(result.get(1).getMetadata().getNamespace(), "default");
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "default", present
	 *     - Service with name "a-1" and namespace "default" present
	 *     - Service with name "b-1" and namespace "default" present
	 *
	 *     As such, there are two matches.
	 * </pre>
	 */
	@Test
	void withFilterSingleEndpointsNoPredicateMatch() {
		Endpoints endpointsA = createEndpoints("a", "default");
		createService("a-1", "default");
		createService("b-1", "default");

		List<Endpoints> result = Fabric8KubernetesDiscoveryClientUtils.withFilter(List.of(endpointsA), PROPERTIES,
				client, x -> !x.getMetadata().getName().contains("1"));
		Assertions.assertEquals(result.size(), 0);
	}

	private Endpoints createEndpoints(String name, String namespace) {
		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withName(name).withNamespace(namespace)
				.endMetadata().build();
		client.endpoints().inNamespace(namespace).resource(endpoints).create();
		return endpoints;
	}

	private void createService(String name, String namespace) {
		Service service = new ServiceBuilder().withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
				.build();
		client.services().inNamespace(namespace).resource(service).create();
	}

}
