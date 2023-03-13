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

package org.springframework.cloud.kubernetes.client.discovery.catalog;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class KubernetesCatalogWatchContextTests {

	@Test
	void emptyLabels() {
		String result = KubernetesCatalogWatchContext.labelSelector(Map.of());
		Assertions.assertEquals("", result);
	}

	@Test
	void singleLabel() {
		String result = KubernetesCatalogWatchContext.labelSelector(Map.of("a", "b"));
		Assertions.assertEquals("a=b", result);
	}

	@Test
	void multipleLabelsLabel() {
		String result = KubernetesCatalogWatchContext.labelSelector(Map.of("a", "b", "c", "d"));
		Assertions.assertTrue(result.contains("c=d"));
		Assertions.assertTrue(result.contains("&"));
		Assertions.assertTrue(result.contains("a=b"));
	}

}
