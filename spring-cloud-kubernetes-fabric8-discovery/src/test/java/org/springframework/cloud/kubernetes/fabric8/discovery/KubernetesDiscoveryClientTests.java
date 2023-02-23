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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
@ExtendWith(OutputCaptureExtension.class)
class KubernetesDiscoveryClientTests {

	private static KubernetesClient client;

	@BeforeAll
	static void setUp() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, client.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterEach
	void afterEach() {
		client.endpoints().inAnyNamespace().delete();
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

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("serviceId");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in all namespaces"));
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

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in all namespaces"));
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

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in all namespaces"));
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

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in all namespaces"));
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

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in all namespaces"));
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

		boolean allNamespaces = true;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(
				result.stream().map(Endpoints::getMetadata).map(ObjectMeta::getNamespace).sorted().toList(),
				List.of("a", "b"));
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in all namespaces"));
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
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("serviceId");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespace : test"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespace : test"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespace : test"));
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

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespace : test"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespace : test"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("service-one");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(
				result.stream().map(Endpoints::getMetadata).map(ObjectMeta::getNamespace).sorted().toList(),
				List.of("test"));
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespace : test"));
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
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("serviceId");
		Assertions.assertEquals(result.size(), 0);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespaces : [test]"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("test");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespaces : [test]"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("a");
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getLabels(), Map.of("color", "blue", "shape", "round"));
		Assertions.assertTrue(output.getOut().contains("searching for endpoints in namespaces : [a]"));
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

		boolean allNamespaces = false;
		Set<String> namespaces = Set.of("a", "b");
		// so that assertion is correct
		String namespacesAsString = namespaces.toString();
		Map<String, String> serviceLabels = Map.of("color", "blue");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, namespaces,
				true, 60L, false, "", Set.of(), serviceLabels, "", null, 0, false);

		KubernetesDiscoveryClient discoveryClient = new KubernetesDiscoveryClient(client, properties, null, null);
		List<Endpoints> result = discoveryClient.getEndPointsList("blue-service");
		Assertions.assertEquals(result.size(), 2);
		Assertions
				.assertTrue(output.getOut().contains("searching for endpoints in namespaces : " + namespacesAsString));
	}

	private void createEndpoints(String namespace, String name, Map<String, String> labels) {
		client.endpoints().inNamespace(namespace)
				.resource(new EndpointsBuilder()
						.withMetadata(new ObjectMetaBuilder().withName(name).withLabels(labels).build()).build())
				.create();
	}

}
