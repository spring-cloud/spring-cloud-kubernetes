/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.leader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class LeaderPropertiesTests {

	@Test
	void getNamespaceNull() {
		LeaderProperties leaderProperties = new LeaderProperties();
		String namespace = leaderProperties.getNamespace("a");
		Assertions.assertEquals("a", namespace);
	}

	@Test
	void getNamespaceEmpty() {
		LeaderProperties leaderProperties = new LeaderProperties();
		leaderProperties.setNamespace("");
		String namespace = leaderProperties.getNamespace("a");
		Assertions.assertEquals("a", namespace);
	}

	@Test
	void getNamespacePresent() {
		LeaderProperties leaderProperties = new LeaderProperties();
		leaderProperties.setNamespace("c");
		String namespace = leaderProperties.getNamespace("a");
		Assertions.assertEquals("c", namespace);
	}

}
