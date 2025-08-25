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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class SourceDataFlattenerTests {

	/**
	 * <pre>
	 *     - nameA -> [a, b]
	 *     - nameB -> [c, d]
	 * </pre>
	 */
	@Test
	void defaultFlattenedSourceDataNoOverlap() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nameA");
		names.add("nameB");

		Map<String, Object> rawData = new HashMap<>();
		rawData.put("nameA", Map.of("a", "b"));
		rawData.put("nameB", Map.of("c", "d"));

		Map<String, Object> result = SourceDataFlattener.defaultFlattenedSourceData(names, rawData);
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("a", "b", "c", "d"));

	}

	/**
	 * <pre>
	 *     - nameA -> [a, b]
	 *     - nameB -> [c, d]
	 *     - nameC -> [a, w]
	 * </pre>
	 */
	@Test
	void defaultFlattenedSourceDataOverlap() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nameA");
		names.add("nameB");
		names.add("nameC");

		Map<String, Object> rawData = new HashMap<>();
		rawData.put("nameA", Map.of("a", "b"));
		rawData.put("nameB", Map.of("c", "d"));
		rawData.put("nameC", Map.of("a", "w"));

		Map<String, Object> result = SourceDataFlattener.defaultFlattenedSourceData(names, rawData);
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("a", "w", "c", "d"));

	}

	/**
	 * <pre>
	 *     - nameA -> [a, b]
	 *     - nameB -> [c, d]
	 *     - prefix -> one
	 * </pre>
	 */
	@Test
	void prefixFlattenedSourceDataNoOverlap() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nameA");
		names.add("nameB");

		Map<String, Object> rawData = new HashMap<>();
		rawData.put("nameA", Map.of("a", "b"));
		rawData.put("nameB", Map.of("c", "d"));

		Map<String, Object> result = SourceDataFlattener.prefixFlattenedSourceData(names, rawData, "one");
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("one.a", "b", "one.c", "d"));

	}

	/**
	 * <pre>
	 *     - nameA -> [a, b]
	 *     - nameB -> [c, d]
	 *     - nameC -> [a, w]
	 *     - prefix -> one
	 * </pre>
	 */
	@Test
	void prefixFlattenedSourceDataOverlap() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nameA");
		names.add("nameB");
		names.add("nameC");

		Map<String, Object> rawData = new HashMap<>();
		rawData.put("nameA", Map.of("a", "b"));
		rawData.put("nameB", Map.of("c", "d"));
		rawData.put("nameC", Map.of("a", "w"));

		Map<String, Object> result = SourceDataFlattener.prefixFlattenedSourceData(names, rawData, "one");
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("one.a", "w", "one.c", "d"));

	}

	/**
	 * <pre>
	 *     - nameA -> [a, b]
	 *     - nameB -> [c, d]
	 * </pre>
	 */
	@Test
	void nameFlattenedSourceDataNoOverlap() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nameA");
		names.add("nameB");

		Map<String, Object> rawData = new HashMap<>();
		rawData.put("nameA", Map.of("a", "b"));
		rawData.put("nameB", Map.of("c", "d"));

		Map<String, Object> result = SourceDataFlattener.nameFlattenedSourceData(names, rawData);
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("nameA.a", "b", "nameB.c", "d"));

	}

	/**
	 * <pre>
	 *     - nameA -> [a, b]
	 *     - nameB -> [c, d]
	 *     - nameC -> [a, w]
	 * </pre>
	 */
	@Test
	void nameFlattenedSourceDataOverlap() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		names.add("nameA");
		names.add("nameB");
		names.add("nameC");

		Map<String, Object> rawData = new HashMap<>();
		rawData.put("nameA", Map.of("a", "b"));
		rawData.put("nameB", Map.of("c", "d"));
		rawData.put("nameC", Map.of("a", "w"));

		Map<String, Object> result = SourceDataFlattener.nameFlattenedSourceData(names, rawData);
		Assertions.assertThat(result)
			.containsExactlyInAnyOrderEntriesOf(Map.of("nameA.a", "b", "nameB.c", "d", "nameC.a", "w"));

	}

}
