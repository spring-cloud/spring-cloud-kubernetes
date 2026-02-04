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

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ExternalNameKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class Fabric8DiscoveryClientTwoTests extends Fabric8DiscoveryClientBase {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.endpoints().inAnyNamespace().delete();
		client.services().inAnyNamespace().delete();
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - there are no endpoints at all
	 *     - as a result we get an empty list
	 * </pre>
	 */
	@Test
	void testAllNamespacesEmpty(CapturedOutput output) {
		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false, null);

		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ExternalName").build())
			.withNewMetadata()
			.withName("endpoint")
			.withNamespace("test")
			.withLabels(serviceLabels)
			.endMetadata()
			.build();
		client.services().inNamespace("test").resource(service).create();

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceId");

		Assertions.assertThat(result).isEmpty();
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in all namespaces");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - one endpoints with labels : "color=blue" and name "blue-service" exists
	 *     - we search for labels : "color=blue" and name "blue-service"
	 *     - we find this endpoints
	 * </pre>
	 */
	@Test
	void testAllNamespacesSingleEndpointsMatchExactLabels(CapturedOutput output) {

		Endpoints endpoints = endpoints("default", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("default").resource(endpoints).create();

		Service service = service("default", "blue-service", Map.of("color", "blue"), Map.of(), Map.of());
		client.services().inNamespace("default").resource(service).create();

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
			true, 60L, false, "", Set.of(), serviceLabels, "",
			KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in all namespaces");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - one endpoints with labels : "color=blue, shape=round" and name "blue-service" exists
	 *     - we search for labels : "color=blue" and name "blue-service"
	 *     - we find this endpoints
	 * </pre>
	 */
	@Test
	void testAllNamespacesSingleEndpointsMatchPartialLabels(CapturedOutput output) {

		Endpoints endpoints = endpoints("default", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("default").resource(endpoints).create();

		Service service = service("default", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("default").resource(service).create();

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
			true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in all namespaces");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - one endpoints with labels : "color=red, shape=round" and name "blue-service" exists
	 *     - we search for labels : "color=red" and name "blue-service"
	 *     - name matches, but labels don't, as such we do not find this endpoints
	 * </pre>
	 */
	@Test
	void testAllNamespacesSingleEndpointsNameMatchesLabelsDont(CapturedOutput output) {

		Endpoints endpoints = endpoints("default", "blue-service", Map.of("color", "red", "shape", "round"), Map.of());
		client.endpoints().inNamespace("default").resource(endpoints).create();

		Service service = service("default", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("default").resource(service).create();

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
			true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT,
			0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");

		Assertions.assertThat(result).isEmpty();
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in all namespaces");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-one" exists
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-two" exists
	 *     - we search for labels : "color=blue" and name "service-one" and find a single service
	 * </pre>
	 */
	@Test
	void testAllNamespacesTwoEndpointsOneMatches(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("default", "service-one", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("default").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("default", "service-two", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("default").resource(endpointsTwo).create();

		Service serviceOne = service("default", "service-one", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("default").resource(serviceOne).create();

		Service serviceTwo = service("default", "service-two", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("default").resource(serviceTwo).create();

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("service-one");

		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in all namespaces");
	}

	/**
	 * <pre>
	 *     - all-namespaces = true
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-one" exists in namespace "a"
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-one" exists in namespace "b"
	 *     - we search for labels : "color=blue" and name "service-one" and find two services
	 * </pre>
	 */
	@Test
	void testAllNamespacesTwoEndpointsInDifferentNamespaces(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("a", "service-one", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("a").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("b", "service-one", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("b").resource(endpointsTwo).create();

		Service serviceOne = service("a", "service-one", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("a").resource(serviceOne).create();

		Service serviceTwo = service("b", "service-one", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("b").resource(serviceTwo).create();

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of(""), client);
		List<ServiceInstance> result = discoveryClient.getInstances("service-one");
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions
			.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("a", "b"));
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in all namespaces");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - there are no endpoints at all
	 *     - as a result we get an empty list
	 * </pre>
	 */
	@Test
	void testClientNamespaceEmpty(CapturedOutput output) {

		Service serviceOne = service("test", "serviceId", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("test");
		Map<String, String> serviceLabels = Map.of();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("serviceId");
		Assertions.assertThat(result).isEmpty();
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - one endpoints with labels : "color=blue" and name "blue-service" exists
	 *     - we search for labels : "color=blue" and name "blue-service"
	 *     - we find this endpoints
	 * </pre>
	 */
	@Test
	void testClientNamespaceSingleEndpointsMatchExactLabels(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("test", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsOne).create();

		Service serviceOne = service("test", "blue-service", Map.of("color", "blue"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - one endpoints with labels : "color=blue, shape=round" and name "blue-service" exists
	 *     - we search for labels : "color=blue" and name "blue-service"
	 *     - we find this endpoints
	 * </pre>
	 */
	@Test
	void testClientNamespaceSingleEndpointsMatchPartialLabels(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("test", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsOne).create();

		Service serviceOne = service("test", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - one endpoints with labels : "color=red, shape=round" and name "blue-service" exists
	 *     - we search for labels : "color=red" and name "blue-service"
	 *     - name matches, but labels don't, as such we do not find this endpoints
	 * </pre>
	 */
	@Test
	void testClientNamespaceSingleEndpointsNameMatchesLabelsDont(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("test", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsOne).create();

		Service serviceOne = service("test", "blue-service", Map.of("color", "red"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result).isEmpty();
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-one" exists
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-two" exists
	 *     - we search for labels : "color=blue" and name "service-one" and find a single service
	 * </pre>
	 */
	@Test
	void testClientNamespaceTwoEndpointsOneMatches(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("test", "service-one", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("test", "service-two", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsTwo).create();

		Service serviceOne = service("test", "service-one", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		Service serviceTwo = service("test", "service-two", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceTwo).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("service-one");
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-one" exists in namespace "test"
	 *     - one endpoints with labels : "color=blue, shape=round" and name "service-one" exists in namespace "b"
	 *     - we search for labels : "color=blue" and name "service-one" and find one service
	 * </pre>
	 */
	@Test
	void testClientNamespaceTwoEndpointsInDifferentNamespaces(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("test", "service-one", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("b", "service-one", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("b").resource(endpointsTwo).create();

		Service serviceOne = service("test", "service-one", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		Service serviceTwo = service("b", "service-one", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("b").resource(serviceTwo).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("service-one");
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions
			.assertThat(result.stream().map(x -> x.getMetadata().get("k8s_namespace")).sorted().toList())
			.isEqualTo(List.of("test"));
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false, selective namespaces = ["test"]
	 *     - there are no endpoints at all
	 *     - as a result we get an empty list
	 * </pre>
	 */
	@Test
	void testSelectiveNamespacesEmpty(CapturedOutput output) {

		Service serviceOne = service("test", "service-one", Map.of("color", "blue"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("test");
		Map<String, String> serviceLabels = Map.of();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("service-one");
		Assertions.assertThat(result).isEmpty();
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false, selective namespaces = ["test"]
	 *     - one endpoints with labels : "color=blue" and name "blue-service" exists in namespace "test"
	 *     - we search for labels : "color=blue" and name "blue-service"
	 *     - we find this endpoints
	 * </pre>
	 */
	@Test
	void testSelectiveNamespacesSingleEndpointsMatchExactLabels(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("test", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("test").resource(endpointsOne).create();

		Service serviceOne = service("test", "blue-service", Map.of("color", "blue"), Map.of(), Map.of());
		client.services().inNamespace("test").resource(serviceOne).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("test");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : test");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false, selective namespaces = ["a", "b']
	 *     - one endpoints with labels : "color=blue, shape=round" and name "blue-service" exists in namespace "a"
	 *     - one endpoints with labels : "color=blue, shape=rectangle" and name "blue-service" exists in namespace "b"
	 *     - we search for labels : "color=blue" and name "blue-service" in namespace "a"
	 *     - we find this endpoints
	 * </pre>
	 */
	@Test
	void testSelectiveNamespacesMultipleNamespacesSingleMatch(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("a", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of());
		client.endpoints().inNamespace("a").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("b", "blue-service", Map.of("color", "blue", "shape", "rectangle"), Map.of());
		client.endpoints().inNamespace("b").resource(endpointsTwo).create();

		Service serviceOne = service("a", "blue-service", Map.of("color", "blue", "shape", "round"), Map.of(), Map.of());
		client.services().inNamespace("a").resource(serviceOne).create();

		Service serviceTwo = service("b", "blue-service", Map.of("color", "blue", "shape", "rectangle"), Map.of(), Map.of());
		client.services().inNamespace("b").resource(serviceTwo).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("a");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("a"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).getMetadata())
			.containsAllEntriesOf(Map.of("color", "blue", "shape", "round"));
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : a");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false, selective namespaces = ["a", "b']
	 *     - one endpoints with labels : "color=blue" and name "blue-service" exists in namespace "a"
	 *     - one endpoints with labels : "color=blue" and name "blue-service" exists in namespace "b"
	 *     - we search for labels : "color=blue" and name "blue-service" in namespace "a" and "b"
	 *     - we find both endpoints
	 * </pre>
	 */
	@Test
	void testSelectiveNamespacesMultipleNamespacesAllMatch(CapturedOutput output) {

		Endpoints endpointsOne = endpoints("a", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("a").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("b", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("b").resource(endpointsTwo).create();

		Service serviceOne = service("a", "blue-service", Map.of("color", "blue"), Map.of(), Map.of());
		client.services().inNamespace("a").resource(serviceOne).create();

		Service serviceTwo = service("b", "blue-service", Map.of("color", "blue"), Map.of(), Map.of());
		client.services().inNamespace("b").resource(serviceTwo).create();

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("a", "b");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("a", "b"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : a");
		Assertions.assertThat(output.getOut()).contains("discovering endpoints in namespace : b");
	}

	/**
	 * <pre>
	 *     - two services are present in two namespaces [a, b]
	 *     - both are returned
	 * </pre>
	 */
	@Test
	void testGetServicesWithExternalNameService() {

		Endpoints endpointsOne = endpoints("a", "blue-service", Map.of(), Map.of());
		client.endpoints().inNamespace("a").resource(endpointsOne).create();

		Endpoints endpointsTwo = endpoints("b", "blue-service", Map.of("color", "blue"), Map.of());
		client.endpoints().inNamespace("b").resource(endpointsTwo).create();

		Service nonExternalNameService = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.withNewMetadata()
			.withName("blue-service")
			.withNamespace("a")
			.endMetadata()
			.build();
		client.services().inNamespace("a").resource(nonExternalNameService).create();

		Service externalNameService = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring").build())
			.withNewMetadata()
			.withName("blue-service")
			.withNamespace("b")
			.endMetadata()
			.build();
		client.services().inNamespace("b").resource(externalNameService).create();

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, true,
				null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("a", "b"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(3);
	}

	@Test
	void testExternalNameService() {
		Service externalNameService = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring-b").build())
			.withNewMetadata()
			.withLabels(Map.of("label-key", "label-value"))
			.withAnnotations(Map.of("abc", "def"))
			.withName("blue-service")
			.withNamespace("b")
			.endMetadata()
			.build();
		client.services().inNamespace("b").resource(externalNameService).create();

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true,
				"labels-prefix-", true, "annotations-prefix-", true, "ports-prefix");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", metadata, 0, false, true, null);

		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("a", "b"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertThat(result.size()).isEqualTo(1);
		ExternalNameKubernetesServiceInstance externalNameServiceInstance = (ExternalNameKubernetesServiceInstance) result
			.get(0);
		Assertions.assertThat(externalNameServiceInstance.getServiceId()).isEqualTo("blue-service");
		Assertions.assertThat(externalNameServiceInstance.getHost()).isEqualTo("k8s-spring-b");
		Assertions.assertThat(externalNameServiceInstance.getPort()).isEqualTo(-1);
		Assertions.assertThat(externalNameServiceInstance.isSecure()).isFalse();
		Assertions.assertThat(externalNameServiceInstance.getUri().toASCIIString()).isEqualTo("k8s-spring-b");
		Assertions.assertThat(externalNameServiceInstance.getMetadata())
			.containsExactlyInAnyOrderEntriesOf(Map.of("k8s_namespace", "b", "labels-prefix-label-key", "label-value",
					"annotations-prefix-abc", "def", "type", "ExternalName"));
	}

	@Test
	void testPodMetadata() {
		Service nonExternalNameService = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
			.withNewMetadata()
			.withName("blue-service")
			.withNamespace("a")
			.endMetadata()
			.build();
		client.services().inNamespace("a").resource(nonExternalNameService).create();

		client.endpoints()
			.inNamespace("a")
			.resource(new EndpointsBuilder().withMetadata(new ObjectMetaBuilder().withName("blue-service").build())
				.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build())
					.withAddresses(new EndpointAddressBuilder().withIp("127.0.0.1")
						.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName("my-pod").build())
						.build())
					.build())
				.build())
			.create();

		client.pods()
			.inNamespace("a")
			.resource(new PodBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("my-pod")
					.withLabels(Map.of("a", "b"))
					.withAnnotations(Map.of("c", "d"))
					.build())
				.build())
			.create();

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true,
				"labels-prefix-", true, "annotations-prefix-", true, "ports-prefix", true, true);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", metadata, 0, false, true, null);


		DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("a", "b"), client);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");

		Assertions.assertThat(result.size()).isEqualTo(1);
		DefaultKubernetesServiceInstance serviceInstance = (DefaultKubernetesServiceInstance) result.get(0);
		Assertions.assertThat(serviceInstance.getServiceId()).isEqualTo("blue-service");
		Assertions.assertThat(serviceInstance.getHost()).isEqualTo("127.0.0.1");
		Assertions.assertThat(serviceInstance.getPort()).isEqualTo(8080);
		Assertions.assertThat(serviceInstance.isSecure()).isFalse();
		Assertions.assertThat(serviceInstance.getUri().toASCIIString()).isEqualTo("http://127.0.0.1:8080");
		Assertions.assertThat(serviceInstance.getMetadata())
			.containsExactlyInAnyOrderEntriesOf(
					Map.of("k8s_namespace", "a", "type", "ClusterIP", "ports-prefix<unset>", "8080"));
		Assertions.assertThat(serviceInstance.podMetadata().get("labels"))
			.containsExactlyInAnyOrderEntriesOf(Map.of("a", "b"));
		Assertions.assertThat(serviceInstance.podMetadata().get("annotations"))
			.containsExactlyInAnyOrderEntriesOf(Map.of("c", "d"));
	}

	@Test
	void testOrder() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, false, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", null, 57, false, false, null);

		Fabric8DiscoveryClient discoveryClient = fabric8DiscoveryClient(properties, List.of("test"), client);

		Assertions.assertThat(discoveryClient.getOrder()).isEqualTo(57);
	}

}
