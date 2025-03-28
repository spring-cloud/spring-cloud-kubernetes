/*
 * Copyright 2013-2022 the original author or authors.
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

/**
 * @author wind57
 *
 * Container that stores multiple sources, mapping their names to their data. We force a
 * LinkedHashMap on purpose, to preserve the order of sources.
 */
public record MultipleSourcesContainer(LinkedHashMap<String, Map<String, Object>> data, boolean error) {

	private static final MultipleSourcesContainer EMPTY = new MultipleSourcesContainer(new LinkedHashMap<>(0), false);

	public static MultipleSourcesContainer empty() {
		return EMPTY;
	}

	/**
	 * Flatten all sources into a single map. For duplicate properties the last source
	 * will be kept.
	 * @return the flattened map
	 */
	public Map<String, Object> flatten() {
		if (error) {
			return Map.of(Constants.ERROR_PROPERTY, "true");
		}

		Map<String, Object> result = new LinkedHashMap<>();
		for (Map<String, Object> data : data.values()) {
			result.putAll(data);
		}
		return result;
	}
}
