/*
 * Copyright 2013-2025 the original author or authors.
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

import java.util.LinkedHashMap;
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
		LinkedHashMap<String, Map<String, Object>> data = new LinkedHashMap<>();
		data.put("nameA", Map.of("a", "b"));
		data.put("nameB", Map.of("c", "d"));

		Map<String, Object> result = SourceDataFlattener.defaultFlattenedSourceData(data);
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
		LinkedHashMap<String, Map<String, Object>> data = new LinkedHashMap<>();
		data.put("nameA", Map.of("a", "b"));
		data.put("nameB", Map.of("c", "d"));
		data.put("nameC", Map.of("a", "w"));

		Map<String, Object> result = SourceDataFlattener.defaultFlattenedSourceData(data);
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
		LinkedHashMap<String, Map<String, Object>> data = new LinkedHashMap<>();
		data.put("nameA", Map.of("a", "b"));
		data.put("nameB", Map.of("c", "d"));

		Map<String, Object> result = SourceDataFlattener.prefixFlattenedSourceData(data, "one");
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
		LinkedHashMap<String, Map<String, Object>> data = new LinkedHashMap<>();
		data.put("nameA", Map.of("a", "b"));
		data.put("nameB", Map.of("c", "d"));
		data.put("nameC", Map.of("a", "w"));

		Map<String, Object> result = SourceDataFlattener.prefixFlattenedSourceData(data, "one");
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
		LinkedHashMap<String, Map<String, Object>> data = new LinkedHashMap<>();
		data.put("nameA", Map.of("a", "b"));
		data.put("nameB", Map.of("c", "d"));

		Map<String, Object> result = SourceDataFlattener.nameFlattenedSourceData(data);
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
		LinkedHashMap<String, Map<String, Object>> data = new LinkedHashMap<>();
		data.put("nameA", Map.of("a", "b"));
		data.put("nameB", Map.of("c", "d"));
		data.put("nameC", Map.of("a", "w"));

		Map<String, Object> result = SourceDataFlattener.nameFlattenedSourceData(data);
		Assertions.assertThat(result)
			.containsExactlyInAnyOrderEntriesOf(Map.of("nameA.a", "b", "nameB.c", "d", "nameC.a", "w"));

	}

}
