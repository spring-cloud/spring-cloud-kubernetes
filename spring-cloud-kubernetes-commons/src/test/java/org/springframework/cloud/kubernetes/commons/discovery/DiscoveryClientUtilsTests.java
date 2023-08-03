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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.client.ServiceInstance;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class DiscoveryClientUtilsTests {

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

		Map<String, String> serviceLabels = Map.of();
		Map<String, String> serviceAnnotations = Map.of();
		Map<String, String> portsData = Map.of();

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result, Map.of("k8s_namespace", "default", "type", "ClusterIP"));
	}

	/**
	 * <pre>
	 *     - labels are not added, though they are not empty
	 *     - annotations are not added, though they are not empty
	 * </pre>
	 */
	@Test
	void testServiceMetadataNotEmptyNotTaken() {
		boolean addLabels = false;
		String labelsPrefix = "";
		boolean addAnnotations = false;
		String annotationsPrefix = "";
		boolean addPorts = false;
		String portsPrefix = "";
		String namespace = "default";

		Map<String, String> serviceLabels = Map.of("a", "1");
		Map<String, String> serviceAnnotations = Map.of("b", "2");
		Map<String, String> portsData = Map.of("c", "3");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result, Map.of("k8s_namespace", "default", "type", "ClusterIP"));
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

		Map<String, String> serviceLabels = Map.of("a", "b");
		Map<String, String> serviceAnnotations = Map.of("c", "2");
		Map<String, String> portsData = Map.of("d", "3");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

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

		Map<String, String> serviceLabels = Map.of("a", "b", "c", "d");
		Map<String, String> serviceAnnotations = Map.of("c", "2");
		Map<String, String> portsData = Map.of("d", "3");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

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

		Map<String, String> serviceLabels = Map.of("a", "b");
		Map<String, String> serviceAnnotations = Map.of("aa", "bb");
		Map<String, String> portsData = Map.of("d", "3");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

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

		Map<String, String> serviceLabels = Map.of("a", "b");
		Map<String, String> serviceAnnotations = Map.of("aa", "bb", "cc", "dd");
		Map<String, String> portsData = Map.of("d", "3");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

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

		Map<String, String> serviceLabels = Map.of("a", "b", "c", "d");
		Map<String, String> serviceAnnotations = Map.of("aa", "bb", "cc", "dd");
		Map<String, String> portsData = Map.of("d", "3");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

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

		Map<String, String> serviceLabels = Map.of("a", "b");
		Map<String, String> serviceAnnotations = Map.of("aa", "bb", "cc", "dd");
		Map<String, String> portsData = Map.of("https", "8080");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

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

		Map<String, String> serviceLabels = Map.of("a", "b");
		Map<String, String> serviceAnnotations = Map.of("aa", "bb", "cc", "dd");
		Map<String, String> portsData = Map.of("http", "8081", "https", "8080");

		KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata(addLabels,
				labelsPrefix, addAnnotations, annotationsPrefix, addPorts, portsPrefix);
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), "", metadata, 0, false, false);

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata("my-service", serviceLabels,
				serviceAnnotations, portsData, properties, namespace, "ClusterIP");

		Assertions.assertEquals(result.size(), 4);
		Assertions.assertEquals(result,
				Map.of("prefix-https", "8080", "prefix-http", "8081", "k8s_namespace", "default", "type", "ClusterIP"));
		Assertions.assertTrue(output.getOut()
				.contains("Adding port metadata: {prefix-http=8081, prefix-https=8080} for serviceId : my-service"));
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

		Map<String, String> serviceLabels = Map.of();

		String result = DiscoveryClientUtils.primaryPortName(properties, serviceLabels, "abc");
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

		Map<String, String> serviceLabels = Map.of();

		String result = DiscoveryClientUtils.primaryPortName(properties, serviceLabels, "abc");
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
		Map<String, String> serviceLabels = Map.of(PRIMARY_PORT_NAME_LABEL_KEY, "https");
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;

		String result = DiscoveryClientUtils.primaryPortName(properties, serviceLabels, "abc");
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
		Map<String, String> serviceLabels = Map.of(PRIMARY_PORT_NAME_LABEL_KEY, "http");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		String result = DiscoveryClientUtils.primaryPortName(properties, serviceLabels, "abc");
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result, "http");
		Assertions.assertTrue(output.getOut().contains("will use primaryPortName : http for service with ID = abc"));
	}

	/**
	 * <pre>
	 *     - EndpointSubset has no ports.
	 * </pre>
	 */
	@Test
	void testEndpointsPortNoPorts(CapturedOutput output) {
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
		Assertions.assertEquals(portData.portNumber(), 0);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(output.getOut().contains("no ports found for service : spring-k8s, will return zero"));
	}

	/**
	 * <pre>
	 *     - EndpointSubset has a single entry in getPorts.
	 * </pre>
	 */
	@Test
	void testEndpointsPortSinglePort(CapturedOutput output) {
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		endpointsPorts.put("http", 8080);
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
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
		String serviceId = "spring-k8s";
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		endpointsPorts.put(null, 8080);
		endpointsPorts.put("not-http-or-https", 8081);
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
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
		String serviceId = "spring-k8s";
		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		endpointsPorts.put("one", 8080);
		endpointsPorts.put("two", 8081);
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
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
		String serviceId = "spring-k8s";
		String primaryPortName = "two";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		endpointsPorts.put("one", 8080);
		endpointsPorts.put("two", 8081);
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
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
		String serviceId = "spring-k8s";
		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false, false);

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		endpointsPorts.put("one", 8080);
		endpointsPorts.put("two", 8081);
		endpointsPorts.put("https", 8082);
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
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
		String serviceId = "spring-k8s";
		String primaryPortName = "three";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), Map.of(), primaryPortName, null, 0, false);

		LinkedHashMap<String, Integer> endpointsPorts = new LinkedHashMap<>();
		endpointsPorts.put("one", 8080);
		endpointsPorts.put("two", 8081);
		endpointsPorts.put("http", 8082);
		Map<String, String> serviceLabels = Map.of();

		ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(endpointsPorts, serviceId, properties,
				serviceLabels);
		Assertions.assertEquals(portData.portNumber(), 8082);
		Assertions.assertEquals(portData.portName(), "http");
		Assertions.assertTrue(
				output.getOut().contains("will use primaryPortName : three for service with ID = spring-k8s"));
		Assertions.assertTrue(output.getOut().contains(
				"not found primary-port-name (with value: 'three') via properties or service labels to match port"));
		Assertions.assertTrue(output.getOut().contains("found primary-port-name via 'http' to match port : 8082"));
	}

	@Test
	void testServiceInstance() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false);
		ServicePortSecureResolver resolver = new ServicePortSecureResolver(properties);

		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		ServiceMetadataForServiceInstance forServiceInstance = new ServiceMetadataForServiceInstance("my-service",
			Map.of(), Map.of());
		InstanceIdHostPodName instanceIdHostPodName = new InstanceIdHostPodName("123", "127.0.0.1", null);
		Map<String, String> serviceMetadata = Map.of("a", "b");

		ServiceInstance serviceInstance = DiscoveryClientUtils.serviceInstance(resolver, forServiceInstance,
			() -> instanceIdHostPodName, null, portData, "my-service", serviceMetadata, "k8s", properties);
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

	@Test
	void testExternalNameServiceInstance() {

		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			false, "", Set.of(), Map.of(), "", KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, false, false);

		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(-1, "http");
		ServiceMetadataForServiceInstance forServiceInstance = new ServiceMetadataForServiceInstance("my-service",
			Map.of(), Map.of());
		InstanceIdHostPodName instanceIdHostPodName = new InstanceIdHostPodName("123", "spring.io", null);
		Map<String, String> serviceMetadata = Map.of("a", "b");

		ServiceInstance serviceInstance = DiscoveryClientUtils.serviceInstance(null, forServiceInstance,
			() -> instanceIdHostPodName, null, portData, "my-service", serviceMetadata, "k8s", properties);

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

	private String filterOnK8sNamespaceAndType(Map<String, String> result) {
		return result.entrySet().stream().filter(en -> !en.getKey().contains("k8s_namespace"))
				.filter(en -> !en.getKey().equals("type"))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).toString();
	}

}
