/*
 * Copyright 2025-present the original author or authors.
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.expression.spel.SpelEvaluationException;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryClientSpelAutoConfigurationTests {

	private final KubernetesClientDiscoveryClientSpelAutoConfiguration toTest = new KubernetesClientDiscoveryClientSpelAutoConfiguration();

	@Test
	void testEmptyExpression() {

		String spelFilter = null;
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false, false, null);

		Predicate<V1Service> predicate = toTest.predicate(properties);
		Assertions.assertThat(predicate).isNotNull();
	}

	@Test
	void testExpressionPresent() {

		String spelFilter = "some";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false, false, null);

		Predicate<V1Service> predicate = toTest.predicate(properties);
		Assertions.assertThat(predicate).isNotNull();
	}

	@Test
	void testTwoServicesBothMatch() {
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false, false, null);

		V1Service a = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-A").withName("a").and().build();

		V1Service b = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-A").withName("a").and().build();

		List<V1Service> unfiltered = List.of(a, b);
		Predicate<V1Service> predicate = toTest.predicate(properties);
		List<V1Service> filtered = unfiltered.stream()
			.filter(predicate)
			.sorted(Comparator.comparing(service -> service.getMetadata().getName()))
			.toList();
		Assertions.assertThat(filtered.get(0).getMetadata().getName()).isEqualTo("a");
		Assertions.assertThat(filtered.get(1).getMetadata().getName()).isEqualTo("a");
	}

	@Test
	void testTwoServicesNoneMatch() {
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false, false, null);

		V1Service a = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		V1Service b = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		List<V1Service> unfiltered = List.of(a, b);
		Predicate<V1Service> predicate = toTest.predicate(properties);
		List<V1Service> filtered = unfiltered.stream()
			.filter(predicate)
			.sorted(Comparator.comparing(service -> service.getMetadata().getName()))
			.toList();
		Assertions.assertThat(filtered).isEmpty();
	}

	@Test
	void testTwoServicesOneMatch() {
		String spelFilter = """
				#root.metadata.namespace matches "^.+A$"
				""";
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false, false, null);

		V1Service a = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		V1Service b = new V1ServiceBuilder().withNewMetadata().withNamespace("namespace-B").withName("a").and().build();

		List<V1Service> unfiltered = List.of(a, b);
		Predicate<V1Service> predicate = toTest.predicate(properties);
		List<V1Service> filtered = unfiltered.stream()
			.filter(predicate)
			.sorted(Comparator.comparing(service -> service.getMetadata().getName()))
			.toList();
		Assertions.assertThat(filtered).isEmpty();
	}

	@Test
	void testPrefix() {
		Predicate<V1Service> predicate = predicate("#root.metadata.name matches '^service.*'");

		Assertions.assertThat(prefixed(predicate)).containsExactly("serviceA", "serviceB");
	}

	@Test
	void testLabels() {
		Predicate<V1Service> predicate = predicate(
				"#root.metadata.labels != null and #root.metadata.labels[\"number\"] != null");

		Assertions.assertThat(labeled(predicate)).containsExactly("withLabel");
	}

	/**
	 * invoking instance methods is not allowed, so this must fail.
	 */
	@Test
	void testInstanceMethodsNotAllowed() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, "#root.metadata.name.startsWith('service')", Set.of(), Map.of(), null, null, 0, false, false,
				null);
		Predicate<V1Service> predicate = toTest.predicate(properties);

		V1Service service = new V1ServiceBuilder().withNewMetadata().withName("serviceA").and().build();
		Assertions.assertThatThrownBy(() -> predicate.test(service))
			.isInstanceOf(SpelEvaluationException.class)
			.hasMessageContaining("EL1004E");
	}

	private Predicate<V1Service> predicate(String spelFilter) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(false, false, Set.of(), true, 60L,
				false, spelFilter, Set.of(), Map.of(), null, null, 0, false, false, null);
		return toTest.predicate(properties);
	}

	private static List<String> prefixed(Predicate<V1Service> predicate) {
		V1Service a = new V1ServiceBuilder().withNewMetadata().withName("serviceA").and().build();
		V1Service b = new V1ServiceBuilder().withNewMetadata().withName("serviceB").and().build();
		V1Service c = new V1ServiceBuilder().withNewMetadata().withName("another").and().build();
		return names(List.of(a, b, c), predicate);
	}

	private static List<String> labeled(Predicate<V1Service> predicate) {
		V1Service withLabel = new V1ServiceBuilder().withNewMetadata()
			.withName("withLabel")
			.withLabels(Map.of("number", "1"))
			.and()
			.build();
		V1Service otherLabel = new V1ServiceBuilder().withNewMetadata()
			.withName("otherLabel")
			.withLabels(Map.of("color", "red"))
			.and()
			.build();
		V1Service noLabels = new V1ServiceBuilder().withNewMetadata().withName("noLabels").and().build();
		return names(List.of(withLabel, otherLabel, noLabels), predicate);
	}

	private static List<String> names(List<V1Service> services, Predicate<V1Service> predicate) {
		return services.stream().filter(predicate).map(service -> service.getMetadata().getName()).sorted().toList();
	}

}
