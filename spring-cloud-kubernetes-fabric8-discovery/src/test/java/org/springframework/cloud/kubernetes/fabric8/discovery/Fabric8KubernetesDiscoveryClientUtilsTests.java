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
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
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
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.services;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8KubernetesDiscoveryClientUtilsTests {

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.services().inAnyNamespace().delete();
	}

	@Test
	void testSubsetsFromEndpointsEmptySubsets() {
		Endpoints endpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("non-default").build()).build();
		EndpointSubsetNS result = Fabric8KubernetesDiscoveryClientUtils.subsetsFromEndpoints(endpoints);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.endpointSubset(), List.of());
		Assertions.assertEquals(result.namespace(), "non-default");
	}

	@Test
	void testSubsetsFromEndpointsNonEmptySubsets() {
		Endpoints endpoints = new EndpointsBuilder().withSubsets((List<EndpointSubset>) null)
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").build())
				.withSubsets(
						new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build()).build())
				.build();
		EndpointSubsetNS result = Fabric8KubernetesDiscoveryClientUtils.subsetsFromEndpoints(endpoints);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.endpointSubset().size(), 1);
		Assertions.assertEquals(result.endpointSubset().get(0).getPorts().get(0).getPort(), 8080);
		Assertions.assertEquals(result.namespace(), "default");

	}

	/*
	 * <pre> - properties do not have primary-port-name set - service labels do not have
	 * primary-port-name set
	 *
	 * As such null is returned. </pre>
	 */
	@Test
	void testPrimaryPortNameNotFound(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNull(result);
		Assertions.assertTrue(output.getOut().contains(
				"did not find a primary-port-name in neither properties nor service labels for service with ID : abc"));
	}

	/*
	 * <pre> - all-namespaces = true - serviceA present in namespace "A" - serviceB
	 * present in namespace "B" - no filters are applied, so both are present </pre>
	 */
	@Test
	void testServicesAllNamespacesNoFilters() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());

		List<Service> result = services(properties, client, null, x -> true, null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.stream().map(s -> s.getMetadata().getName()).sorted().toList(),
				List.of("serviceA", "serviceB"));
	}

	/**
	 * <pre>
	 *     - properties do have primary-port-name set to "https"
	 *     - service labels do not have primary-port-name set
	 *
	 *     As such "https" is returned.
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameFoundInProperties(CapturedOutput output) {
		String primaryPortName = "https";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, primaryPortName);
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : https for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - properties do not have primary-port-name set
	 *     - service labels do have primary-port-name set to "https"
	 *
	 *     As such "https" is returned.
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameFoundInLabels(CapturedOutput output) {
		Map<String, String> labels = Map.of(PRIMARY_PORT_NAME_LABEL_KEY, "https");
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, "https");
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : https for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - properties do have primary-port-name set to "https"
	 *     - service labels do have primary-port-name set to "http"
	 *
	 *     As such "http" is returned (labels win).
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameFoundInBothPropertiesAndLabels(CapturedOutput output) {
		String primaryPortName = "https";
		Map<String, String> labels = Map.of(PRIMARY_PORT_NAME_LABEL_KEY, "http");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build()).build();

		String result = Fabric8KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, "http");
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : http for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - EndpointSubset has a single entry in getPorts.
	 * </pre>
	 */
	@Test
	void testEndpointsPortSinglePort(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("http").build()).build();
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8080);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(output.getOut().contains("endpoint ports has a single entry, using port : 8080"));
	}

	/**
	 * <pre>
	 *     - primary-port-name is null.
	 * </pre>
	 */
	@Test
	void testEndpointsPortNullPrimaryPortName(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).build(),
						new EndpointPortBuilder().withPort(8081).build())
				.build();
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8080);
		Assertions.assertNull(portData.portName());
		Assertions.assertTrue(output.getOut().contains(
				"did not find a primary-port-name in neither properties nor service labels for service with ID : spring-k8s"));
		Assertions.assertTrue(output.getOut()
				.contains("not found primary-port-name (with value: 'null') via properties or service labels"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'https' to match port"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'http' to match port"));
		Assertions.assertTrue(output.getOut().contains("""
				Make sure that either the primary-port-name label has been added to the service,
				or spring.cloud.kubernetes.discovery.primary-port-name has been configured.
				Alternatively name the primary port 'https' or 'http'
				An incorrect configuration may result in non-deterministic behaviour."""));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "three", such a port name does not exist.
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortNameIsPresentButNotFound(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8080);
		Assertions.assertEquals(portData.portName(), "one");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut()
				.contains("not found primary-port-name (with value: 'three') via properties or service labels"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'https' to match port"));
		Assertions.assertTrue(output.getOut().contains("not found primary-port-name via 'http' to match port"));
		Assertions.assertTrue(output.getOut().contains("""
				Make sure that either the primary-port-name label has been added to the service,
				or spring.cloud.kubernetes.discovery.primary-port-name has been configured.
				Alternatively name the primary port 'https' or 'http'
				An incorrect configuration may result in non-deterministic behaviour."""));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "two", such a port name exists and matches 8081
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortNameFound(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "two";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8081);
		Assertions.assertEquals(portData.portName(), "two");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : two for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"found primary-port-name (with value: 'two') via properties or service labels to match port : 8081"));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "three", such a port name does not exist.
	 *     - https port exists and this one is returned
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortHttps(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build(),
						new EndpointPortBuilder().withPort(8082).withName("https").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8082);
		Assertions.assertEquals(portData.portName(), "https");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"not found primary-port-name (with value: 'three') via properties or service labels to match port"));
		Assertions.assertTrue(output.getOut().contains("found primary-port-name via 'https' to match port : 8082"));
	}

	/**
	 * <pre>
	 *     - primary-port-name is "three", such a port name does not exist.
	 *     - http port exists and this one is returned
	 * </pre>
	 */
	@Test
	void testEndpointsPortPrimaryPortHttp(CapturedOutput output) {
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withPorts(new EndpointPortBuilder().withPort(8080).withName("one").build(),
						new EndpointPortBuilder().withPort(8081).withName("two").build(),
						new EndpointPortBuilder().withPort(8082).withName("http").build())
				.build();
		String serviceId = "spring-k8s";

		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Fabric8ServicePortData portData = Fabric8KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId,
				properties, service);
		Assertions.assertEquals(portData.portNumber(), 8082);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"not found primary-port-name (with value: 'three') via properties or service labels to match port"));
		Assertions.assertTrue(output.getOut().contains("found primary-port-name via 'http' to match port : 8082"));
	}

	/**
	 * <pre>
	 *     - labels are not added
	 *     - annotations are not added
	 * </pre>
	 */
	@Test
	void testServiceMetadataEmpty() {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result, Map.of("k8s_namespace", "default", "type", "ClusterIP"));
	}

	/*
	 * <pre> - all-namespaces = true - serviceA present in namespace "A" - serviceB
	 * present in namespace "B" - we search only for "serviceA" filter, so only one is
	 * returned </pre>
	 */
	@Test
	void testServicesAllNamespacesNameFilter() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());

		List<Service> result = services(properties, client, null, x -> true, Map.of("metadata.name", "serviceA"),
				"fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - labels are added without a prefix
	 *     - annotations are not added
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddLabelsNoPrefix(CapturedOutput output) {
		boolean addLabels = true;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("a", "b")).build()).build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result, Map.of("a", "b", "k8s_namespace", "default", "type", "ClusterIP"));
		String labelsMetadata = filterOnK8sNamespaceAndType(result);
		Assertions.assertTrue(
				output.getOut().contains("Adding labels metadata: " + labelsMetadata + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are added with prefix
	 *     - annotations are not added
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddLabelsWithPrefix(CapturedOutput output) {
		boolean addLabels = true;
		String labelsPrefix = "prefix-";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("a", "b", "c", "d")).build()).build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
				Map.of("prefix-a", "b", "prefix-c", "d", "k8s_namespace", "default", "type", "ClusterIP"));
		// so that result is deterministic in assertion
		String labelsMetadata = filterOnK8sNamespaceAndType(result);
		Assertions.assertTrue(
				output.getOut().contains("Adding labels metadata: " + labelsMetadata + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are not added
	 *     - annotations are added without prefix
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddAnnotationsNoPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = true;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb")).withLabels(Map.of("a", "b"))
						.build())
				.build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result, Map.of("aa", "bb", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions
				.assertTrue(output.getOut().contains("Adding annotations metadata: {aa=bb} for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are not added
	 *     - annotations are added with prefix
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddAnnotationsWithPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = true;
		String annotationsPrefix = "prefix-";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb", "cc", "dd"))
						.withLabels(Map.of("a", "b")).build())
				.build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
				Map.of("prefix-aa", "bb", "prefix-cc", "dd", "k8s_namespace", "default", "type", "ClusterIP"));
		// so that result is deterministic in assertion
		String annotations = filterOnK8sNamespaceAndType(result);
		Assertions.assertTrue(
				output.getOut().contains("Adding annotations metadata: " + annotations + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - labels are added with prefix
	 *     - annotations are added with prefix
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddLabelsAndAnnotationsWithPrefix(CapturedOutput output) {
		boolean addLabels = true;
		String labelsPrefix = "label-";
		boolean addAnnotations = true;
		String annotationsPrefix = "annotation-";
		boolean addPorts = false;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ClusterIP").build()).withMetadata(new ObjectMetaBuilder()
						.withAnnotations(Map.of("aa", "bb", "cc", "dd")).withLabels(Map.of("a", "b", "c", "d")).build())
				.build();

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, List.of(), namespace);
		Assertions.assertEquals(result.size(), 6);
		Assertions.assertEquals(result, Map.of("annotation-aa", "bb", "annotation-cc", "dd", "label-a", "b", "label-c",
				"d", "k8s_namespace", "default", "type", "ClusterIP"));
		// so that result is deterministic in assertion
		String labels = result.entrySet().stream().filter(en -> en.getKey().contains("label"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).toString();
		String annotations = result.entrySet().stream().filter(en -> en.getKey().contains("annotation"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).toString();
		Assertions.assertTrue(
				output.getOut().contains("Adding labels metadata: " + labels + " for serviceId: my-service"));
		Assertions.assertTrue(
				output.getOut().contains("Adding annotations metadata: " + annotations + " for serviceId: my-service"));
	}

	/**
	 * <pre>
	 *     - ports without prefix are added
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddPortsWithoutPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "prefix-";
		boolean addPorts = true;
		String portsPrefix = "";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb", "cc", "dd"))
						.withLabels(Map.of("a", "b")).build())
				.build();

		List<EndpointSubset> endpointSubsets = List.of(
				new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8081).withName("").build())
						.build(),
				new EndpointSubsetBuilder()
						.withPorts(new EndpointPortBuilder().withPort(8080).withName("https").build()).build());

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, endpointSubsets, namespace);
		Assertions.assertEquals(result.size(), 3);
		Assertions.assertEquals(result, Map.of("https", "8080", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions
				.assertTrue(output.getOut().contains("Adding port metadata: {https=8080} for serviceId : my-service"));
	}

	/**
	 * <pre>
	 *     - ports without prefix are added
	 * </pre>
	 */
	@Test
	void testServiceMetadataAddPortsWithPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "prefix-";
		boolean addPorts = true;
		String portsPrefix = "prefix-";

		String namespace = "default";

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb", "cc", "dd"))
						.withLabels(Map.of("a", "b")).build())
				.build();

		List<EndpointSubset> endpointSubsets = List.of(
				new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8081).withName("http").build())
						.build(),
				new EndpointSubsetBuilder()
						.withPorts(new EndpointPortBuilder().withPort(8080).withName("https").build()).build());

		Map<String, String> result = Fabric8KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service,
				properties, endpointSubsets, namespace);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
				Map.of("prefix-https", "8080", "prefix-http", "8081", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions.assertTrue(output.getOut()
				.contains("Adding port metadata: {prefix-http=8081, prefix-https=8080} for serviceId : my-service"));
	}

	/**
	 * <pre>
	 *      - ready addresses are empty
	 *      - not ready addresses are not included
	 * </pre>
	 */
	@Test
	void testEmptyAddresses() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder().build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 0);
	}

	/**
	 * <pre>
	 *      - ready addresses has two entries
	 *      - not ready addresses are not included
	 * </pre>
	 */
	@Test
	void testReadyAddressesOnly() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
						new EndpointAddressBuilder().withHostname("two").build())
				.build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 2);
	}

	/**
	 * <pre>
	 *      - ready addresses has two entries
	 *      - not ready addresses has a single entry, but we do not take it
	 * </pre>
	 */
	@Test
	void testReadyAddressesTakenNotReadyAddressesNotTaken() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
						new EndpointAddressBuilder().withHostname("two").build())
				.withNotReadyAddresses(new EndpointAddressBuilder().withHostname("three").build()).build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 2);
		List<String> hostNames = addresses.stream().map(EndpointAddress::getHostname).sorted().toList();
		Assertions.assertEquals(hostNames, List.of("one", "two"));
	}

	/**
	 * <pre>
	 *      - ready addresses has two entries
	 *      - not ready addresses has a single entry, but we do not take it
	 * </pre>
	 */
	@Test
	void testBothAddressesTaken() {
		boolean includeNotReadyAddresses = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		EndpointSubset endpointSubset = new EndpointSubsetBuilder()
				.withAddresses(new EndpointAddressBuilder().withHostname("one").build(),
						new EndpointAddressBuilder().withHostname("two").build())
				.withNotReadyAddresses(new EndpointAddressBuilder().withHostname("three").build()).build();
		List<EndpointAddress> addresses = Fabric8KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 3);
		List<String> hostNames = addresses.stream().map(EndpointAddress::getHostname).sorted().toList();
		Assertions.assertEquals(hostNames, List.of("one", "three", "two"));
	}

	@Test
	void testServiceInstance() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false);
		ServicePortSecureResolver resolver = new ServicePortSecureResolver(properties);
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();
		EndpointAddress address = new EndpointAddressBuilder().withNewTargetRef().withUid("123").endTargetRef()
				.withIp("127.0.0.1").build();

		Fabric8ServicePortData portData = new Fabric8ServicePortData(8080, "http");
		ServiceInstance serviceInstance = Fabric8KubernetesDiscoveryClientUtils.serviceInstance(resolver, service,
				address, portData, "my-service", Map.of("a", "b"), "k8s", properties, null);
		Assertions.assertTrue(serviceInstance instanceof DefaultKubernetesServiceInstance);
		DefaultKubernetesServiceInstance defaultInstance = (DefaultKubernetesServiceInstance) serviceInstance;
		Assertions.assertEquals(defaultInstance.getInstanceId(), "123");
		Assertions.assertEquals(defaultInstance.getServiceId(), "my-service");
		Assertions.assertEquals(defaultInstance.getHost(), "127.0.0.1");
		Assertions.assertEquals(defaultInstance.getPort(), 8080);
		Assertions.assertFalse(defaultInstance.isSecure());
		Assertions.assertEquals(defaultInstance.getUri().toASCIIString(), "http://127.0.0.1:8080");
		Assertions.assertEquals(defaultInstance.getMetadata(), Map.of("a", "b"));
		Assertions.assertEquals(defaultInstance.getScheme(), "http");
		Assertions.assertEquals(defaultInstance.getNamespace(), "k8s");
		Assertions.assertNull(defaultInstance.getCluster());
	}

	/*
	 * <pre> - all-namespaces = true - serviceA present in namespace "A" - serviceB
	 * present in namespace "B" - we search with a filter where a label with name "letter"
	 * and value "b" is present </pre>
	 */
	@Test
	void testServicesAllNamespacesPredicateFilter() {
		boolean allNamespaces = true;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of("letter", "a"));
		service("serviceB", "B", Map.of("letter", "b"));

		List<Service> result = services(properties, client, null,
				x -> x.getMetadata().getLabels().equals(Map.of("letter", "b")), null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : [A, B]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - serviceC present in namespace "C"
	 *     - we search in namespaces [A, B], as such two services are returned
	 * </pre>
	 */
	@Test
	void testServicesSelectiveNamespacesNoFilters() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());
		service("serviceC", "C", Map.of());

		List<Service> result = services(properties, client, null, x -> true, null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.stream().map(x -> x.getMetadata().getName()).sorted().toList(),
				List.of("serviceA", "serviceB"));
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : [A, B]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - serviceC present in namespace "C"
	 *     - we search in namespaces [A, B] with name filter = "serviceA", so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesSelectiveNamespacesNameFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());
		service("serviceC", "C", Map.of());

		List<Service> result = services(properties, client, null, x -> true, Map.of("metadata.name", "serviceA"),
				"fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : [A, B]
	 *     - serviceA present in namespace "A" with labels [letter, a]
	 *     - serviceB present in namespace "B" with labels [letter, b]
	 *     - serviceC present in namespace "C" with labels [letter, c]
	 *     - we search in namespaces [A, B] with predicate filter = [letter, b], so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesSelectiveNamespacesPredicateFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces,
				Set.of("A", "B"), true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of("letter", "a"));
		service("serviceB", "B", Map.of("letter", "b"));
		service("serviceC", "C", Map.of("letter", "c"));

		List<Service> result = services(properties, client, null,
				x -> x.getMetadata().getLabels().equals(Map.of("letter", "b")), null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : []
	 *     - namespace from kubernetes namespace provider = [A]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "B"
	 *     - serviceC present in namespace "C"
	 *     - we search in namespaces [A], as such we get one service
	 * </pre>
	 */
	@Test
	void testServicesNamespaceProviderNoFilters() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "B", Map.of());
		service("serviceC", "C", Map.of());

		List<Service> result = services(properties, client, namespaceProvider("A"), x -> true, null,
				"fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : []
	 *     - namespace from kubernetes namespace provider = [A]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "A"
	 *     - we search in namespaces [A] with name filter = "serviceA", so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesNamespaceProviderNameFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of());
		service("serviceB", "A", Map.of());

		List<Service> result = services(properties, client, namespaceProvider("A"), x -> true,
				Map.of("metadata.name", "serviceA"), "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceA");
	}

	/**
	 * <pre>
	 *     - all-namespaces = false
	 *     - selective namespaces : []
	 *     - namespace from kubernetes namespace provider = [A]
	 *     - serviceA present in namespace "A"
	 *     - serviceB present in namespace "A"
	 *     - we search in namespaces [A] with predicate filter = [letter, b], so we get a single result
	 * </pre>
	 */
	@Test
	void testServicesNamespaceProviderPredicateFilter() {
		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);
		service("serviceA", "A", Map.of("letter", "a"));
		service("serviceB", "A", Map.of("letter", "b"));

		List<Service> result = services(properties, client, namespaceProvider("A"),
				x -> x.getMetadata().getLabels().equals(Map.of("letter", "b")), null, "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "serviceB");
	}

	@Test
	void testExternalName() {
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withType("ExternalName").withExternalName("k8s-spring").build())
				.withNewMetadata().withName("external-name-service").and().build();
		client.services().inNamespace("test").resource(service).create();

		boolean allNamespaces = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, allNamespaces, Set.of(),
				true, 60L, false, "", Set.of(), Map.of(), "", null, 0, false);

		List<Service> result = services(properties, client, namespaceProvider("test"),
				x -> x.getSpec().getType().equals("ExternalName"), Map.of(), "fabric8-discovery");
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(0).getMetadata().getName(), "external-name-service");
	}

	private void service(String name, String namespace, Map<String, String> labels) {
		Service service = new ServiceBuilder().withNewMetadata().withName(name).withLabels(labels)
				.withNamespace(namespace).and().build();
		client.services().inNamespace(namespace).resource(service).create();
	}

	private KubernetesNamespaceProvider namespaceProvider(String namespace) {
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", namespace);
		return new KubernetesNamespaceProvider(mockEnvironment);
	}

	@Test
	void testExternalNameServiceInstance() {
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withExternalName("spring.io").withType("ExternalName").build())
				.withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		Fabric8ServicePortData portData = new Fabric8ServicePortData(-1, "http");
		ServiceInstance serviceInstance = Fabric8KubernetesDiscoveryClientUtils.serviceInstance(null, service, null,
				portData, "my-service", Map.of("a", "b"), "k8s", KubernetesDiscoveryProperties.DEFAULT, null);
		Assertions.assertTrue(serviceInstance instanceof DefaultKubernetesServiceInstance);
		DefaultKubernetesServiceInstance defaultInstance = (DefaultKubernetesServiceInstance) serviceInstance;
		Assertions.assertEquals(defaultInstance.getInstanceId(), "123");
		Assertions.assertEquals(defaultInstance.getServiceId(), "my-service");
		Assertions.assertEquals(defaultInstance.getHost(), "spring.io");
		Assertions.assertEquals(defaultInstance.getPort(), -1);
		Assertions.assertFalse(defaultInstance.isSecure());
		Assertions.assertEquals(defaultInstance.getUri().toASCIIString(), "spring.io");
		Assertions.assertEquals(defaultInstance.getMetadata(), Map.of("a", "b"));
		Assertions.assertEquals(defaultInstance.getScheme(), "http");
		Assertions.assertEquals(defaultInstance.getNamespace(), "k8s");
		Assertions.assertNull(defaultInstance.getCluster());
	}

	@Test
	void testNoPortsServiceInstance() {
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().withType("ClusterIP").build())
				.withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		EndpointAddress endpointAddress = new EndpointAddressBuilder().withIp("127.0.0.1").build();

		Fabric8ServicePortData portData = new Fabric8ServicePortData(0, "http");
		ServiceInstance serviceInstance = Fabric8KubernetesDiscoveryClientUtils.serviceInstance(null, service,
				endpointAddress, portData, "my-service", Map.of("a", "b"), "k8s", KubernetesDiscoveryProperties.DEFAULT,
				null);
		Assertions.assertTrue(serviceInstance instanceof DefaultKubernetesServiceInstance);
		DefaultKubernetesServiceInstance defaultInstance = (DefaultKubernetesServiceInstance) serviceInstance;
		Assertions.assertEquals(defaultInstance.getInstanceId(), "123");
		Assertions.assertEquals(defaultInstance.getServiceId(), "my-service");
		Assertions.assertEquals(defaultInstance.getHost(), "127.0.0.1");
		Assertions.assertEquals(defaultInstance.getScheme(), "http");
		Assertions.assertEquals(defaultInstance.getPort(), 0);
		Assertions.assertFalse(defaultInstance.isSecure());
		Assertions.assertEquals(defaultInstance.getUri().toASCIIString(), "http://127.0.0.1");
		Assertions.assertEquals(defaultInstance.getMetadata(), Map.of("a", "b"));
		Assertions.assertEquals(defaultInstance.getNamespace(), "k8s");
		Assertions.assertNull(defaultInstance.getCluster());
	}

	private String filterOnK8sNamespaceAndType(Map<String, String> result) {
		return result.entrySet().stream().filter(en -> !en.getKey().contains("k8s_namespace"))
				.filter(en -> !en.getKey().equals("type"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).toString();
	}

}
