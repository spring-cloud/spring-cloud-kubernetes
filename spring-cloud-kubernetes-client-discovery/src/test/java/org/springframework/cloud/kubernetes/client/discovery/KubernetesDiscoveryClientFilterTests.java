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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesDiscoveryClientFilterTests {

	@Test
	void testEmptyExpression(CapturedOutput output) {

		String spelFilter = null;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false);

		Predicate<V1Service> predicate = KubernetesDiscoveryClientUtils.filter(properties);
		Assertions.assertNotNull(predicate);
		Assertions.assertTrue(output.getOut().contains("filter not defined, returning always true predicate"));
	}

	@Test
	void testExpressionPresent(CapturedOutput output) {

		String spelFilter = "some";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false);

		Predicate<V1Service> predicate = KubernetesDiscoveryClientUtils.filter(properties);
		Assertions.assertNotNull(predicate);
		Assertions.assertTrue(output.getOut().contains("returning predicate based on filter expression: some"));
	}

	@Test
	void testTwoServicesBothMatch() {
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false);

		V1Service a = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-A").withName("a").and().build();

		V1Service b = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-A").withName("a").and().build();

		List<V1Service> unfiltered = List.of(a, b);
		Predicate<V1Service> predicate = KubernetesDiscoveryClientUtils.filter(properties);
		List<V1Service> filtered = unfiltered.stream().filter(predicate)
				.sorted(Comparator.comparing(service -> service.getMetadata().getName())).toList();
		Assertions.assertEquals(filtered.get(0).getMetadata().getName(), "a");
		Assertions.assertEquals(filtered.get(1).getMetadata().getName(), "a");
	}

	@Test
	void testTwoServicesNoneMatch() {
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false);

		V1Service a = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		V1Service b = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		List<V1Service> unfiltered = List.of(a, b);
		Predicate<V1Service> predicate = KubernetesDiscoveryClientUtils.filter(properties);
		List<V1Service> filtered = unfiltered.stream().filter(predicate)
				.sorted(Comparator.comparing(service -> service.getMetadata().getName())).toList();
		Assertions.assertEquals(filtered.size(), 0);
	}

	@Test
	void testTwoServicesOneMatch() {
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false);

		V1Service a = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		V1Service b = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		List<V1Service> unfiltered = List.of(a, b);
		Predicate<V1Service> predicate = KubernetesDiscoveryClientUtils.filter(properties);
		List<V1Service> filtered = unfiltered.stream().filter(predicate)
				.sorted(Comparator.comparing(service -> service.getMetadata().getName())).toList();
		Assertions.assertEquals(filtered.size(), 0);
	}

}
