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
import java.util.stream.Collectors;

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

@EnableKubernetesMockClient(crud = true, https = false)
class KubernetesDiscoveryClientServiceInstanceTests {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.endpoints().inAnyNamespace().delete();
		client.services().inAnyNamespace().delete();
	}

	@Test
	void withFilterEmptyInput() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("empty");
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
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setFilter("#root.metadata.namespace matches '^.*namespace.*$'");
		properties.setAllNamespaces(true);
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a", "namespace-a");
		createService("a", "namespace-not-a");

		List<Endpoints> result = discoveryClient.getEndPointsList("a");
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
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);
		properties.setFilter("#root.metadata.namespace matches '^.*a$'");
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a", "namespace-a");
		createService("a", "namespace-a");

		List<Endpoints> result = discoveryClient.getEndPointsList("a");
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
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);
		properties.setFilter("#root.metadata.namespace matches '^.*a$'");
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a", "namespace-a");
		createEndpoints("b", "namespace-b");
		createService("a", "namespace-a");

		List<Endpoints> result = discoveryClient.getEndPointsList("a");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "a");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespace-a");
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
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);
		properties.setFilter("#root.metadata.namespace matches 'namespace.*$'");
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a", "namespace-a");
		createEndpoints("b", "namespace-b");
		createService("a", "namespace-a");
		createService("b", "namespace-b");
		createService("c", "namespace-c");

		List<Endpoints> result = discoveryClient.getEndPointsList("a");
		result.addAll(discoveryClient.getEndPointsList("b"));
		Assertions.assertEquals(result.size(), 2);
		result = result.stream().sorted(Comparator.comparing(x -> x.getMetadata().getName()))
				.collect(Collectors.toList());
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "a");
		Assertions.assertEquals(result.get(0).getMetadata().getNamespace(), "namespace-a");
		Assertions.assertEquals(result.get(1).getMetadata().getName(), "b");
		Assertions.assertEquals(result.get(1).getMetadata().getNamespace(), "namespace-b");
	}

	/**
	 * <pre>
	 *     - Endpoints with name : "a" and namespace "namespace-a", present
	 *     - Service with name "a" and namespace "namespace-a" present
	 *
	 *     As such, there is a single match.
	 * </pre>
	 */
	@Test
	void withFilterSingleEndpointsMatchesFilter() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);
		properties.setFilter("#root.metadata.namespace matches '^namespace-a$'");
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a", "namespace-a");
		createService("a", "namespace-a");

		List<Endpoints> result = discoveryClient.getEndPointsList("a");
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
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);
		properties.setFilter("#root.metadata.namespace matches '^default$'");
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a-1", "default");
		createEndpoints("b-1", "default");
		createEndpoints("c-2", "default");
		createService("a-1", "default");
		createService("b-1", "default");

		List<Endpoints> result = discoveryClient.getEndPointsList("a-1");
		result.addAll(discoveryClient.getEndPointsList("b-1"));
		result.addAll(discoveryClient.getEndPointsList("c-1"));
		Assertions.assertEquals(result.size(), 2);
		result = result.stream().sorted(Comparator.comparing(x -> x.getMetadata().getName()))
				.collect(Collectors.toList());
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
	 *     As such, there is not match.
	 * </pre>
	 */
	@Test
	void withFilterSingleEndpointsNoPredicateMatch() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.setAllNamespaces(true);
		properties.setFilter("#root.metadata.namespace matches '^default$'");
		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null);

		createEndpoints("a", "default");
		createService("a-1", "default");
		createService("b-1", "default");

		List<Endpoints> result = discoveryClient.getEndPointsList("a-1");
		Assertions.assertEquals(result.size(), 0);
	}

	private Endpoints createEndpoints(String name, String namespace) {
		Endpoints endpoints = new EndpointsBuilder().withNewMetadata().withName(name).withNamespace(namespace)
				.endMetadata().build();
		client.endpoints().inNamespace(namespace).create(endpoints);
		return endpoints;
	}

	private void createService(String name, String namespace) {
		Service service = new ServiceBuilder().withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
				.build();
		client.services().inNamespace(namespace).create(service);
	}

}
