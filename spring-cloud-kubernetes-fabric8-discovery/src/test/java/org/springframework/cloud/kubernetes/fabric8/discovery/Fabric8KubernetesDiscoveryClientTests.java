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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class Fabric8KubernetesDiscoveryClientTests {

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
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("serviceId");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in all namespaces"));
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

		createEndpoints("default", "blue-service", Map.of("color", "blue"));
		createService("default", "blue-service", Map.of("color", "blue"));

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in all namespaces"));
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

		createEndpoints("default", "blue-service", Map.of("color", "blue", "shape", "round"));
		createService("default", "blue-service", Map.of("color", "blue", "shape", "round"));

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in all namespaces"));
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

		createEndpoints("default", "blue-service", Map.of("color", "red", "shape", "round"));

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in all namespaces"));
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

		createEndpoints("default", "service-one", Map.of("color", "blue", "shape", "round"));
		createEndpoints("default", "service-two", Map.of("color", "blue", "shape", "round"));

		createService("default", "service-one", Map.of("color", "blue", "shape", "round"));
		createService("default", "service-two", Map.of("color", "blue", "shape", "round"));

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in all namespaces"));
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

		createEndpoints("a", "service-one", Map.of("color", "blue", "shape", "round"));
		createEndpoints("b", "service-one", Map.of("color", "blue", "shape", "round"));

		createService("a", "service-one", Map.of("color", "blue", "shape", "round"));
		createService("b", "service-one", Map.of("color", "blue", "shape", "round"));

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(
				result.stream().map(Endpoints::getMetadata).map(ObjectMeta::getNamespace).sorted().toList(),
				List.of("a", "b"));
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in all namespaces"));
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
		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("serviceId");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespace : test"));
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

		createEndpoints("test", "blue-service", Map.of("color", "blue"));
		createService("test", "blue-service", Map.of("color", "blue"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespace : test"));
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

		createEndpoints("test", "blue-service", Map.of("color", "blue", "shape", "round"));
		createService("test", "blue-service", Map.of("color", "blue", "shape", "round"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespace : test"));
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

		createEndpoints("test", "blue-service", Map.of("color", "red", "shape", "round"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespace : test"));
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

		createEndpoints("test", "service-one", Map.of("color", "blue", "shape", "round"));
		createEndpoints("test", "service-two", Map.of("color", "blue", "shape", "round"));

		createService("test", "service-one", Map.of("color", "blue", "shape", "round"));
		createService("test", "service-two", Map.of("color", "blue", "shape", "round"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespace : test"));
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

		createEndpoints("test", "service-one", Map.of("color", "blue", "shape", "round"));
		createEndpoints("b", "service-one", Map.of("color", "blue", "shape", "round"));

		createService("test", "service-one", Map.of("color", "blue", "shape", "round"));
		createService("b", "service-one", Map.of("color", "blue", "shape", "round"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(
				result.stream().map(Endpoints::getMetadata).map(ObjectMeta::getNamespace).sorted().toList(),
				List.of("test"));
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespace : test"));
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
		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("test");
		Map<String, String> serviceLabels = Map.of();
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("serviceId");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespaces : [test]"));
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

		createEndpoints("test", "blue-service", Map.of("color", "blue"));
		createService("test", "blue-service", Map.of("color", "blue"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("test");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, x -> true,
				null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespaces : [test]"));
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

		createEndpoints("a", "blue-service", Map.of("color", "blue", "shape", "round"));
		createEndpoints("b", "blue-service", Map.of("color", "blue", "shape", "rectangle"));

		createService("a", "blue-service", Map.of("color", "blue", "shape", "round"));
		createService("b", "blue-service", Map.of("color", "blue", "shape", "rectangle"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("a");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getLabels(), Map.of("color", "blue", "shape", "round"));
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespaces : [a]"));
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

		createEndpoints("a", "blue-service", Map.of("color", "blue"));
		createEndpoints("b", "blue-service", Map.of("color", "blue"));

		createService("a", "blue-service", Map.of("color", "blue"));
		createService("b", "blue-service", Map.of("color", "blue"));

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("a", "b");
		// so that assertion is correct
		String namespacesAsString = namespaces.toString();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertTrue(output.getOut().contains("discovering endpoints in namespaces : " + namespacesAsString));
	}

	/**
	 * <pre>
	 *     - two services are present in two namespaces [a, b]
	 *     - both are returned
	 * </pre>
	 */
	@Test
	void testGetServicesWithExternalNameService() {
		Service nonExternalNameService = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).withNewMetadata()
				.withName("blue-service").withNamespace("a").endMetadata().build();
		client.services().inNamespace("a").resource(nonExternalNameService).create();

		Service externalNameService = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring").build())
				.withNewMetadata().withName("blue-service").withNamespace("b").endMetadata().build();
		client.services().inNamespace("b").resource(externalNameService).create();

		// last argument is irrelevant, as getServices does not care about that flag
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<String> result = discoveryClient.getServices();
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0), "blue-service");
	}

	@Test
	void testExternalNameService() {
		Service externalNameService = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring-b").build())
				.withNewMetadata().withLabels(Map.of("label-key", "label-value")).withAnnotations(Map.of("abc", "def"))
				.withName("blue-service").withNamespace("b").endMetadata().build();
		client.services().inNamespace("b").resource(externalNameService).create();

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true,
				"labels-prefix-", true, "annotations-prefix-", true, "ports-prefix");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", metadata, 0, false, true);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertEquals(result.size(), 1);
		DefaultKubernetesServiceInstance externalNameServiceInstance = (DefaultKubernetesServiceInstance) result.get(0);
		Assertions.assertEquals(externalNameServiceInstance.getServiceId(), "blue-service");
		Assertions.assertEquals(externalNameServiceInstance.getHost(), "k8s-spring-b");
		Assertions.assertEquals(externalNameServiceInstance.getPort(), -1);
		Assertions.assertFalse(externalNameServiceInstance.isSecure());
		Assertions.assertEquals(externalNameServiceInstance.getUri().toASCIIString(), "k8s-spring-b");
		Assertions.assertEquals(externalNameServiceInstance.getMetadata(), Map.of("k8s_namespace", "b",
				"labels-prefix-label-key", "label-value", "annotations-prefix-abc", "def", "type", "ExternalName"));
	}

	@Test
	void testPodMetadata() {
		Service nonExternalNameService = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).withNewMetadata()
				.withName("blue-service").withNamespace("a").endMetadata().build();
		client.services().inNamespace("a").resource(nonExternalNameService).create();

		client.endpoints().inNamespace("a").resource(new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withName("blue-service").build())
				.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build())
						.withAddresses(new EndpointAddressBuilder().withIp("127.0.0.1")
								.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName("my-pod").build())
								.build())
						.build())
				.build()).create();

		client.pods().inNamespace("a").resource(new PodBuilder().withMetadata(new ObjectMetaBuilder().withName("my-pod")
				.withLabels(Map.of("a", "b")).withAnnotations(Map.of("c", "d")).build()).build()).create();

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(true,
				"labels-prefix-", true, "annotations-prefix-", true, "ports-prefix", true, true);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of("a", "b"), true,
				60L, false, "", Set.of(), Map.of(), "", metadata, 0, false, true);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null, null);
		List<ServiceInstance> result = discoveryClient.getInstances("blue-service");
		Assertions.assertEquals(result.size(), 1);
		DefaultKubernetesServiceInstance serviceInstance = (DefaultKubernetesServiceInstance) result.get(0);
		Assertions.assertEquals(serviceInstance.getServiceId(), "blue-service");
		Assertions.assertEquals(serviceInstance.getHost(), "127.0.0.1");
		Assertions.assertEquals(serviceInstance.getPort(), 8080);
		Assertions.assertFalse(serviceInstance.isSecure());
		Assertions.assertEquals(serviceInstance.getUri().toASCIIString(), "http://127.0.0.1:8080");
		Assertions.assertEquals(serviceInstance.getMetadata(),
				Map.of("k8s_namespace", "a", "type", "ClusterIP", "ports-prefix<unset>", "8080"));
		Assertions.assertEquals(serviceInstance.podMetadata().get("labels"), Map.of("a", "b"));
		Assertions.assertEquals(serviceInstance.podMetadata().get("annotations"), Map.of("c", "d"));
	}

	private void createEndpoints(String namespace, String name, Map<String, String> labels) {
		client.endpoints().inNamespace(namespace)
				.resource(new EndpointsBuilder()
						.withMetadata(new ObjectMetaBuilder().withName(name).withLabels(labels).build()).build())
				.create();
	}

	private void createService(String namespace, String name, Map<String, String> labels) {
		client.services().inNamespace(namespace)
				.resource(new ServiceBuilder()
						.withMetadata(new ObjectMetaBuilder().withName(name).withLabels(labels).build()).build())
				.create();
	}

}
