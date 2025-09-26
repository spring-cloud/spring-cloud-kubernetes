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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author wind57
 */
final class SourceDataFlattener {

	private SourceDataFlattener() {

	}

	/**
	 * Flattens the data from rawData without any additional processing.
	 */
	static Map<String, Object> defaultFlattenedSourceData(LinkedHashMap<String, Map<String, Object>> sourceData) {
		Map<String, Object> flattenedData = new HashMap<>();
		sourceData.values().forEach(flattenedData::putAll);
		return flattenedData;
	}

	/**
	 * Flattens the data from rawData by adding a prefix for each key.
	 */
	static Map<String, Object> prefixFlattenedSourceData(LinkedHashMap<String, Map<String, Object>> sourceData,
			String prefix) {
		Map<String, Object> flattenedData = new HashMap<>();
		sourceData.values().forEach(data -> data.forEach((key, value) -> flattenedData.put(prefix + "." + key, value)));
		return flattenedData;
	}

	/**
	 * Flattens the data from rawData by adding a prefix for each key, which is equal to
	 * the source name.
	 */
	static Map<String, Object> nameFlattenedSourceData(LinkedHashMap<String, Map<String, Object>> sourceData) {
		Map<String, Object> flattenedData = new HashMap<>();
		sourceData.forEach((name, data) -> data.forEach((key, value) -> flattenedData.put(name + "." + key, value)));
		return flattenedData;
	}

}
