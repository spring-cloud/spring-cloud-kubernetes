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

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * @author wind57
 *
 * Container that stores multiple sources, to be exact their names and their flattenned
 * data. We force a LinkedHashSet on purpose, to preserve the order of sources.
 */
public record MultipleSourcesContainer(LinkedHashSet<String> names, Map<String, Object> data) {

	private static final MultipleSourcesContainer EMPTY = new MultipleSourcesContainer(new LinkedHashSet<>(0),
			Map.of());

	public static MultipleSourcesContainer empty() {
		return EMPTY;
	}
}
