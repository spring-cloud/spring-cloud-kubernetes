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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadataForServiceInstance;

import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.matchesServiceLabels;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesDiscoveryClientUtilsTests {

	/**
	 * properties service labels are empty
	 */
	@Test
	void testEmptyServiceLabelsFromProperties(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = KubernetesDiscoveryProperties.DEFAULT;
		V1Service service = new V1ServiceBuilder().withMetadata(new V1ObjectMeta().name("my-service")).build();

		boolean result = matchesServiceLabels(service, properties);
		Assertions.assertTrue(result);
		Assertions.assertTrue(output.getOut()
				.contains("service labels from properties are empty, service with name : 'my-service' will match"));
	}

	/**
	 * labels from service are empty
	 */
	@Test
	void testEmptyServiceLabelsFromService(CapturedOutput output) {
		Map<String, String> propertiesLabels = Map.of("key", "value");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), propertiesLabels, "", null, 0, false);
		V1Service service = new V1ServiceBuilder().withMetadata(new V1ObjectMeta().name("my-service")).build();

		boolean result = matchesServiceLabels(service, properties);
		Assertions.assertFalse(result);
		Assertions.assertTrue(output.getOut().contains("service with name : 'my-service' does not have labels"));
	}

	/**
	 * <pre>
	 *     properties = [a=b]
	 *     service    = [a=b]
	 *
	 *     This means the service is picked-up.
	 * </pre>
	 */
	@Test
	void testOne(CapturedOutput output) {
		Map<String, String> propertiesLabels = Map.of("a", "b");
		Map<String, String> serviceLabels = Map.of("a", "b");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), propertiesLabels, "", null, 0, false);
		V1Service service = new V1ServiceBuilder()
				.withMetadata(new V1ObjectMeta().labels(serviceLabels).name("my-service")).build();

		boolean result = matchesServiceLabels(service, properties);
		Assertions.assertTrue(result);
		Assertions.assertTrue(output.getOut().contains("Service labels from properties : {a=b}"));
		Assertions.assertTrue(output.getOut().contains("Service labels from service : {a=b}"));
	}

	/**
	 * <pre>
	 *     properties = [a=b, c=d]
	 *     service    = [a=b]
	 *
	 *     This means the service is not picked-up.
	 * </pre>
	 */
	@Test
	void testTwo(CapturedOutput output) {
		Map<String, String> propertiesLabels = ordered(Map.of("a", "b", "c", "d"));
		Map<String, String> serviceLabels = Map.of("a", "b");
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), propertiesLabels, "", null, 0, false);
		V1Service service = new V1ServiceBuilder()
				.withMetadata(new V1ObjectMeta().labels(serviceLabels).name("my-service")).build();

		boolean result = matchesServiceLabels(service, properties);
		Assertions.assertFalse(result);
		Assertions.assertTrue(output.getOut().contains("Service labels from properties : {a=b, c=d}"));
		Assertions.assertTrue(output.getOut().contains("Service labels from service : {a=b}"));
	}

	/**
	 * <pre>
	 *     properties = [a=b, c=d]
	 *     service    = [a=b, c=d]
	 *
	 *     This means the service is picked-up.
	 * </pre>
	 */
	@Test
	void testThree(CapturedOutput output) {
		Map<String, String> propertiesLabels = ordered(Map.of("a", "b", "c", "d"));
		Map<String, String> serviceLabels = ordered(Map.of("a", "b", "c", "d"));
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), propertiesLabels, "", null, 0, false);
		V1Service service = new V1ServiceBuilder()
				.withMetadata(new V1ObjectMeta().labels(serviceLabels).name("my-service")).build();

		boolean result = matchesServiceLabels(service, properties);
		Assertions.assertTrue(result);
		Assertions.assertTrue(output.getOut().contains("Service labels from properties : {a=b, c=d}"));
		Assertions.assertTrue(output.getOut().contains("Service labels from service : {a=b, c=d}"));
	}

	/**
	 * <pre>
	 *     properties = [a=b]
	 *     service    = [a=b, c=d]
	 *
	 *     This means the service is picked-up.
	 * </pre>
	 */
	@Test
	void testFour(CapturedOutput output) {
		Map<String, String> propertiesLabels = Map.of("a", "b");
		Map<String, String> serviceLabels = ordered(Map.of("a", "b", "c", "d"));
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
				true, "", Set.of(), propertiesLabels, "", null, 0, false);
		V1Service service = new V1ServiceBuilder()
				.withMetadata(new V1ObjectMeta().labels(serviceLabels).name("my-service")).build();

		boolean result = matchesServiceLabels(service, properties);
		Assertions.assertTrue(result);
		Assertions.assertTrue(output.getOut().contains("Service labels from properties : {a=b}"));
		Assertions.assertTrue(output.getOut().contains("Service labels from service : {a=b, c=d}"));
	}

	@Test
	void endpointSubsetPortsDataNullPorts() {
		V1EndpointSubset subset = new V1EndpointSubset();
		Assertions.assertNull(subset.getPorts());

		LinkedHashMap<String, Integer> result = KubernetesDiscoveryClientUtils.endpointSubsetPortsData(subset);
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void endpointSubsetPortsDataEmptyPorts() {
		V1EndpointSubset subset = new V1EndpointSubset().ports(List.of());
		Assertions.assertTrue(subset.getPorts().isEmpty());

		LinkedHashMap<String, Integer> result = KubernetesDiscoveryClientUtils.endpointSubsetPortsData(subset);
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void endpointSubsetPortsDataOnePortNullName() {
		V1EndpointSubset subset = new V1EndpointSubset().ports(List.of(
			new CoreV1EndpointPort().port(8080)
		));
		Assertions.assertEquals(subset.getPorts().size(), 1);
		Assertions.assertNull(subset.getPorts().get(0).getName());

		LinkedHashMap<String, Integer> result = KubernetesDiscoveryClientUtils.endpointSubsetPortsData(subset);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get(null), 8080);
	}

	@Test
	void endpointSubsetPortsDataOnePort() {
		V1EndpointSubset subset = new V1EndpointSubset().ports(List.of(
			new CoreV1EndpointPort().port(8080).name("http")
		));

		LinkedHashMap<String, Integer> result = KubernetesDiscoveryClientUtils.endpointSubsetPortsData(subset);
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get("http"), 8080);
	}

	@Test
	void endpointSubsetPortsDataThreePorts() {
		V1EndpointSubset subset = new V1EndpointSubset().ports(List.of(
			new CoreV1EndpointPort().port(8080).name("http"),
			new CoreV1EndpointPort().port(8081).name("https"),
			new CoreV1EndpointPort().port(8082)
		));

		LinkedHashMap<String, Integer> result = KubernetesDiscoveryClientUtils.endpointSubsetPortsData(subset);
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get("http"), 8080);
		Assertions.assertEquals(result.get("https"), 8081);
	}

	@Test
	void portsDataEmptyInput() {
		Map<String, String> result = KubernetesDiscoveryClientUtils.portsData(List.of());
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void portsDataNullEndpointSubsets() {
		V1EndpointSubset endpointSubset = new V1EndpointSubset().ports(null);
		Assertions.assertNull(endpointSubset.getPorts());

		Map<String, String> result = KubernetesDiscoveryClientUtils.portsData(List.of(endpointSubset));
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void portsDataEmptyEndpointSubsets() {
		V1EndpointSubset endpointSubset = new V1EndpointSubset().ports(List.of());
		Assertions.assertNotNull(endpointSubset.getPorts());

		Map<String, String> result = KubernetesDiscoveryClientUtils.portsData(List.of(endpointSubset));
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void portsDataOneEndpointSubsetsWithoutName() {
		V1EndpointSubset endpointSubset = new V1EndpointSubset().ports(List.of(
			new CoreV1EndpointPort().port(8080)
		));
		Assertions.assertNotNull(endpointSubset.getPorts());

		Map<String, String> result = KubernetesDiscoveryClientUtils.portsData(List.of(endpointSubset));
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void portsDataOneEndpointSubsetsWithName() {
		V1EndpointSubset endpointSubset = new V1EndpointSubset().ports(List.of(
			new CoreV1EndpointPort().port(8080).name("http")
		));
		Assertions.assertNotNull(endpointSubset.getPorts());

		Map<String, String> result = KubernetesDiscoveryClientUtils.portsData(List.of(endpointSubset));
		Assertions.assertEquals(result.size(), 1);
		Assertions.assertEquals(result.get("http"), "8080");
	}

	@Test
	void portsDataThreeEndpointSubsets() {
		V1EndpointSubset endpointSubset = new V1EndpointSubset().ports(List.of(
			new CoreV1EndpointPort().port(8080).name("http"),
			new CoreV1EndpointPort().port(8081).name("https"),
			new CoreV1EndpointPort().port(8082)
		));
		Assertions.assertNotNull(endpointSubset.getPorts());

		Map<String, String> result = KubernetesDiscoveryClientUtils.portsData(List.of(endpointSubset));
		Assertions.assertEquals(result.size(), 2);
		Assertions.assertEquals(result.get("http"), "8080");
		Assertions.assertEquals(result.get("https"), "8081");
	}

	/**
	 * <pre>
	 *      - ready addresses are null
	 *      - not ready addresses are not included
	 * </pre>
	 */
	@Test
	void testNullAddresses() {
		boolean includeNotReadyAddresses = false;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60L,
			includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false, false);
		V1EndpointSubset endpointSubset = new V1EndpointSubset();
		Assertions.assertNull(endpointSubset.getAddresses());

		List<V1EndpointAddress> addresses = KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 0);
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
		V1EndpointSubset endpointSubset = new V1EndpointSubset().addresses(List.of());
		Assertions.assertEquals(endpointSubset.getAddresses().size(), 0);

		List<V1EndpointAddress> addresses = KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
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
			includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false);
		V1EndpointSubset endpointSubset = new V1EndpointSubset()
			.addresses(List.of(
				new V1EndpointAddress().hostname("one"),
				new V1EndpointAddress().hostname("two")));
		List<V1EndpointAddress> addresses = KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
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
		V1EndpointSubset endpointSubset = new V1EndpointSubset()
			.addresses(List.of(new V1EndpointAddress().hostname("one"),
				new V1EndpointAddress().hostname("two")))
			.notReadyAddresses(List.of(new V1EndpointAddress().hostname("three")));
		List<V1EndpointAddress> addresses = KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 2);
		List<String> hostNames = addresses.stream().map(V1EndpointAddress::getHostname).sorted().toList();
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
			includeNotReadyAddresses, "", Set.of(), Map.of(), "", null, 0, false);
		V1EndpointSubset endpointSubset = new V1EndpointSubset()
			.addresses(List.of(new V1EndpointAddress().hostname("one"),
				new V1EndpointAddress().hostname("two")))
			.notReadyAddresses(List.of(new V1EndpointAddress().hostname("three")));
		List<V1EndpointAddress> addresses = KubernetesDiscoveryClientUtils.addresses(endpointSubset, properties);
		Assertions.assertEquals(addresses.size(), 3);
		List<String> hostNames = addresses.stream().map(V1EndpointAddress::getHostname).sorted().toList();
		Assertions.assertEquals(hostNames, List.of("one", "three", "two"));
	}

	@Test
	void forServiceInstanceNullMetadata() {
		V1Service service = new V1Service();
		Assertions.assertNull(service.getMetadata());

		ServiceMetadataForServiceInstance forServiceInstance =
			KubernetesDiscoveryClientUtils.forServiceInstance(service);
		Assertions.assertNotNull(forServiceInstance);
		Assertions.assertNull(forServiceInstance.name());
		Assertions.assertTrue(forServiceInstance.labels().isEmpty());
		Assertions.assertTrue(forServiceInstance.annotations().isEmpty());
	}

	@Test
	void forServiceInstanceEmptyMetadata() {
		V1Service service = new V1Service().metadata(new V1ObjectMeta());

		ServiceMetadataForServiceInstance forServiceInstance =
			KubernetesDiscoveryClientUtils.forServiceInstance(service);
		Assertions.assertNotNull(forServiceInstance);
		Assertions.assertNull(forServiceInstance.name());
		Assertions.assertTrue(forServiceInstance.labels().isEmpty());
		Assertions.assertTrue(forServiceInstance.annotations().isEmpty());
	}

	@Test
	void forServiceInstancePresentMetadata() {
		V1Service service = new V1Service().metadata(new V1ObjectMeta()
			.name("name").labels(Map.of("a", "b")).annotations(Map.of("c", "d")));

		ServiceMetadataForServiceInstance forServiceInstance =
			KubernetesDiscoveryClientUtils.forServiceInstance(service);
		Assertions.assertNotNull(forServiceInstance);
		Assertions.assertEquals(forServiceInstance.name(), "name");
		Assertions.assertEquals(forServiceInstance.labels(), Map.of("a", "b"));
		Assertions.assertEquals(forServiceInstance.annotations(), Map.of("c", "d"));
	}

	// preserve order for testing reasons
	private Map<String, String> ordered(Map<String, String> input) {
		return input.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
	}

}
