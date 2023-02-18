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

import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesDiscoveryClientUtilsTests {

	@Test
	void testSubsetsFromEndpointsEmptySubsets() {
		Endpoints endpoints = new EndpointsBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("non-default").build()).build();
		EndpointSubsetNS result = KubernetesDiscoveryClientUtils.subsetsFromEndpoints(endpoints);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.endpointSubset(), List.of());
		Assertions.assertEquals(result.namespace(), "non-default");
	}

	@Test
	void testSubsetsFromEndpointsNonEmptySubsets() {
		Endpoints endpoints = new EndpointsBuilder().withSubsets((List<EndpointSubset>) null)
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").build())
				.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(8080).build()).build())
				.build();
		EndpointSubsetNS result = KubernetesDiscoveryClientUtils.subsetsFromEndpoints(endpoints);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.endpointSubset().size(), 1);
		Assertions.assertEquals(result.endpointSubset().get(0).getPorts().get(0).getPort(), 8080);
		Assertions.assertEquals(result.namespace(), "default");
	}

	/**
	 * <pre>
	 *     - properties do not have primary-port-name set
	 *     - service labels do not have primary-port-name set
	 *
	 *     As such null is returned.
	 * </pre>
	 */
	@Test
	void testPrimaryPortNameNotFound(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		String result = KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
		Assertions.assertNull(result);
		Assertions.assertTrue(output.getOut().contains(
				"did not find a primary-port-name in neither properties nor service labels for service with ID : abc"));
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
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		String result = KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
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

		String result = KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
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
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withLabels(labels).build()).build();

		String result = KubernetesDiscoveryClientUtils.primaryPortName(properties, service, "abc");
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
				.withPorts(new EndpointPortBuilder().withPort(8080).build()).build();
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		Service service = new ServiceBuilder().build();

		Integer port = KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId, properties, service);
		Assertions.assertEquals(port, 8080);
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

		Integer port = KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId, properties, service);
		Assertions.assertEquals(port, 8080);
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

		Integer port = KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId, properties, service);
		Assertions.assertEquals(port, 8080);
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

		Integer port = KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId, properties, service);
		Assertions.assertEquals(port, 8081);
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
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		Service service = new ServiceBuilder().withMetadata(new ObjectMeta()).build();

		Integer port = KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId, properties, service);
		Assertions.assertEquals(port, 8082);
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

		Integer port = KubernetesDiscoveryClientUtils.endpointsPort(endpointSubset, serviceId, properties, service);
		Assertions.assertEquals(port, 8082);
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
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, false, "");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service, properties);
		Assertions.assertEquals(result.size(), 0);
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
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, false, "");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("a", "b")).build()).build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service, properties);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result, Map.of("a", "b"));
		Assertions.assertTrue(output.getOut().contains("Adding labels metadata: {a=b} for serviceId: my-service"));
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
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, false, "");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder()
				.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("a", "b", "c", "d")).build()).build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service, properties);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result, Map.of("prefix-a", "b", "prefix-c", "d"));
		// so that result is deterministic in assertion
		String labels = result.toString();
		Assertions.assertTrue(
				output.getOut().contains("Adding labels metadata: " + labels + " for serviceId: my-service"));
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
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, false, "");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().withMetadata(
				new ObjectMetaBuilder().withAnnotations(Map.of("aa", "bb")).withLabels(Map.of("a", "b")).build())
				.build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service, properties);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result, Map.of("aa", "bb"));
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
	void testServiceMetadataAddAnnotationsWithPrefixPrefix(CapturedOutput output) {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = true;
		String annotationsPrefix = "prefix-";
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, false, "");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder()
				.withAnnotations(Map.of("aa", "bb", "cc", "dd")).withLabels(Map.of("a", "b")).build()).build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service, properties);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result, Map.of("prefix-aa", "bb", "prefix-cc", "dd"));
		// so that result is deterministic in assertion
		String annotations = result.toString();
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
		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, false, "");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false);
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder()
				.withAnnotations(Map.of("aa", "bb", "cc", "dd")).withLabels(Map.of("a", "b", "c", "d")).build())
				.build();

		Map<String, String> result = KubernetesDiscoveryClientUtils.serviceMetadata("my-service", service, properties);
		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
				Map.of("annotation-aa", "bb", "annotation-cc", "dd", "label-a", "b", "label-c", "d"));
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

}
