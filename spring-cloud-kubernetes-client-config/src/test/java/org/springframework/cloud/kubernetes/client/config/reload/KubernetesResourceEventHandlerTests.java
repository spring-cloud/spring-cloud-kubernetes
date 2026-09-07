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

package org.springframework.cloud.kubernetes.client.config.reload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesResourceEventHandlerTests {

	private static final KubernetesResourceEventHandler<?> NOOP_RESOURCE_HANDLER = new KubernetesResourceEventHandler<>(
			x -> {

			});

	@Test
	void onAddPassesResourceToConsumer() {
		List<V1ConfigMap> configMapEvents = new ArrayList<>();
		KubernetesResourceEventHandler<V1ConfigMap> handler = new KubernetesResourceEventHandler<>(
				configMapEvents::add);
		V1ConfigMap configMap = configMap(Map.of("one", "1"));

		handler.onAdd(configMap);

		assertThat(configMapEvents).containsExactly(configMap);
	}

	@Test
	void onUpdatePassesNewResourceToConsumerWhenDataChanged() {
		List<V1ConfigMap> configMapEvents = new ArrayList<>();
		KubernetesResourceEventHandler<V1ConfigMap> handler = new KubernetesResourceEventHandler<>(
				configMapEvents::add);
		V1ConfigMap oldConfigMap = configMap(Map.of("one", "1"));
		V1ConfigMap newConfigMap = configMap(Map.of("one", "2"));

		handler.onUpdate(oldConfigMap, newConfigMap);

		assertThat(configMapEvents).containsExactly(newConfigMap);
	}

	@Test
	void onUpdateDoesNotPassResourceToConsumerWhenDataDidNotChange() {
		List<V1ConfigMap> configMapEvents = new ArrayList<>();
		KubernetesResourceEventHandler<V1ConfigMap> handler = new KubernetesResourceEventHandler<>(
				configMapEvents::add);
		V1ConfigMap oldConfigMap = configMap(Map.of("one", "1"));
		V1ConfigMap newConfigMap = configMap(Map.of("one", "1"));

		handler.onUpdate(oldConfigMap, newConfigMap);

		assertThat(configMapEvents).isEmpty();
	}

	@Test
	void onUpdatePassesNewSecretToConsumerWhenDataChanged() {
		List<V1Secret> secretEvents = new ArrayList<>();
		KubernetesResourceEventHandler<V1Secret> secretHandler = new KubernetesResourceEventHandler<>(
				secretEvents::add);
		V1Secret oldSecret = secret(Map.of("one", "1".getBytes(StandardCharsets.UTF_8)));
		V1Secret newSecret = secret(Map.of("one", "2".getBytes(StandardCharsets.UTF_8)));

		secretHandler.onUpdate(oldSecret, newSecret);

		assertThat(secretEvents).containsExactly(newSecret);
	}

	@Test
	void onUpdateDoesNotPassSecretToConsumerWhenByteArrayContentDidNotChange() {
		List<V1Secret> secretEvents = new ArrayList<>();
		KubernetesResourceEventHandler<V1Secret> secretHandler = new KubernetesResourceEventHandler<>(
				secretEvents::add);
		V1Secret oldSecret = secret(Map.of("one", "1".getBytes(StandardCharsets.UTF_8)));
		V1Secret newSecret = secret(Map.of("one", "1".getBytes(StandardCharsets.UTF_8)));

		secretHandler.onUpdate(oldSecret, newSecret);

		assertThat(secretEvents).isEmpty();
	}

	@Test
	void onDeletePassesResourceToConsumer() {
		List<V1ConfigMap> configMapEvents = new ArrayList<>();
		KubernetesResourceEventHandler<V1ConfigMap> handler = new KubernetesResourceEventHandler<>(
				configMapEvents::add);
		V1ConfigMap configMap = configMap(Map.of("one", "1"));

		handler.onDelete(configMap, false);

		assertThat(configMapEvents).containsExactly(configMap);
	}

	/**
	 * both are null, treat that as no change.
	 */
	@Test
	void secretDataIsEqualWhenBothMapsAreNull() {
		Map<String, byte[]> left = null;
		Map<String, byte[]> right = null;

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isTrue();
	}

	/**
	 * - left is empty map - right is null
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void secretDataIsEqualWhenLeftMapIsEmptyAndRightMapIsNull() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = null;

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isTrue();
	}

	/**
	 * - left is null - right is empty map
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void secretDataIsEqualWhenLeftMapIsNullAndRightMapIsEmpty() {
		Map<String, byte[]> left = null;
		Map<String, byte[]> right = Map.of();

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isTrue();
	}

	/**
	 * - left is empty map - right is empty map
	 *
	 * treat as equal, that is: no change
	 */
	@Test
	void secretDataIsEqualWhenBothMapsAreEmpty() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = Map.of();

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isTrue();
	}

	/**
	 * - left is empty map - right is [1, b]
	 *
	 * treat as non-equal, that is change
	 */
	@Test
	void secretDataIsNotEqualWhenRightMapContainsAnAdditionalEntry() {
		Map<String, byte[]> left = Map.of();
		Map<String, byte[]> right = Map.of("1", "b".getBytes());

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isFalse();
	}

	/**
	 * - left is [1, a] - right is [1, b]
	 *
	 * treat as non-equal, that is change
	 */
	@Test
	void secretDataIsNotEqualWhenAnEntryValueChanges() {
		Map<String, byte[]> left = Map.of("1", "a".getBytes());
		Map<String, byte[]> right = Map.of("1", "b".getBytes());

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isFalse();
	}

	/**
	 * - left is [1, a, 2 aa] - right is [1, b, 2, aa]
	 *
	 * treat as non-equal, that is change
	 */
	@Test
	void secretDataIsNotEqualWhenOneOfMultipleEntryValuesChanges() {
		Map<String, byte[]> left = Map.of("1", "a".getBytes(), "2", "aa".getBytes());
		Map<String, byte[]> right = Map.of("1", "b".getBytes(), "2", "aa".getBytes());

		boolean result = NOOP_RESOURCE_HANDLER.secretDataEquals(left, right);
		assertThat(result).isFalse();
	}

	private V1ConfigMap configMap(Map<String, String> data) {
		return new V1ConfigMap().kind("ConfigMap")
			.metadata(new V1ObjectMeta().name("config-map").namespace("default"))
			.data(data);
	}

	private V1Secret secret(Map<String, byte[]> data) {
		return new V1Secret().kind("Secret")
			.metadata(new V1ObjectMeta().name("secret").namespace("default"))
			.data(data);
	}

}
