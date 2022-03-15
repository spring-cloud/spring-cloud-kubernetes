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

import java.util.Collections;
import java.util.Map;

/**
 * Holds the data for a certain source. For example the contents (sourceData) for secret
 * with name sourceName.
 *
 * @author wind57
 */
public final record SourceData(String sourceName, Map<String, Object> sourceData) {

	public static SourceData emptyRecord(String sourceName) {
		return new SourceData(sourceName, Collections.emptyMap());
	}

}
