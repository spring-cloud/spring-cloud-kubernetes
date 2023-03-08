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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

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

	// preserve order for testing reasons
	private Map<String, String> ordered(Map<String, String> input) {
		return input.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
	}

}
