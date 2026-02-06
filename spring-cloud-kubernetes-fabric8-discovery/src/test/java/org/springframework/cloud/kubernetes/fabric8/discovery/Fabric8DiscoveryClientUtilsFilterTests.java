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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8DiscoveryClientUtilsFilterTests extends Fabric8DiscoveryClientBase {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.endpoints().inAnyNamespace().delete();
		client.services().inAnyNamespace().delete();
	}

	/**
	 * no services, no endpoints.
	 */
	@Test
	void emptyInput() {

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", null, 0, false, true, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("a", "b"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("a");

		Assertions.assertThat(result).isEmpty();
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
	void endpointsNoMatchInService() {
		createEndpoints("a", "namespace-a");
		createService("a", "namespace-not-a");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true,
				Set.of("namespace-not-a"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false, true, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespace-not-a"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("a");

		Assertions.assertThat(result).isEmpty();
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
	void endpointsMatchInService() {
		createEndpoints("a", "namespace-a");
		createService("a", "namespace-a");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("namespace-a"),
				true, 60L, false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false,
				true, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespace-a"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("a");
		Assertions.assertThat(result.size()).isEqualTo(1);
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
	void endpointsOneMatchInService() {
		createEndpoints("a", "namespace-a");
		createEndpoints("b", "namespace-b");

		createService("a", "namespace-a");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("namespace-a"),
				true, 60L, false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false,
				true, null);
		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespace-a"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("a");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata().get("k8s_namespace")).isEqualTo("namespace-a");
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Endpoints with name : "a" and namespace "namespace-b", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *     - Service with name "a" and namespace "namespace-b" present
	 *     - Service with name "c" and namespace "namespace-c" present
	 *
	 *     As such, there are two matches.
	 * </pre>
	 */
	@Test
	void endpointsAndThreeServices() {
		createEndpoints("a", "namespace-a");
		createEndpoints("a", "namespace-b");

		createService("a", "namespace-a");
		createService("a", "namespace-b");
		createService("c", "namespace-c");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false,
				Set.of("namespace-a", "namespace-b"), true, 60L, false, "", Set.of(), Map.of(), "",
				KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, true, null);
		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("namespace-a", "namespace-b"),
				client);
		List<ServiceInstance> result = discoveryClient.getInstances("a");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("namespace-a", "namespace-b"));

	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a-1" and namespace "namespace-a", present
	 *     - Endpoints with name : "a-1" and namespace "namespace-b", present
	 *     - Endpoints with name : "a-1" and namespace "namespace-c", present
	 *     - Service with name "a-1" and namespace "default" present
	 *     - Service with name "b-1" and namespace "default" present
	 *
	 *     As such, there are two matches.
	 * </pre>
	 */
	@Test
	void endpointsMatchesFilterAllNamespaces() {
		createEndpoints("a-1", "namespace-a");
		createEndpoints("a-1", "namespace-b");
		createEndpoints("a-1", "namespace-c");

		createService("a-1", "namespace-a");
		createService("a-1", "namespace-b");

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, true,
				null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("a-1");

		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("namespace-a", "namespace-b"));
	}

	private void createEndpoints(String name, String namespace) {
		Endpoints endpoints = endpoints(namespace, name, Map.of(), Map.of());
		client.endpoints().inNamespace(namespace).resource(endpoints).create();
	}

	private void createService(String name, String namespace) {
		Service service = service(namespace, name, Map.of(), Map.of(), Map.of());
		client.services().inNamespace(namespace).resource(service).create();
	}

}
