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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class StrictProfileTests {

	@Test
	void testAllNonStrict() {
		String[] profiles = new String[] { "a", "b" };
		LinkedHashSet<StrictProfile> result = StrictProfile.allNonStrict(profiles);

		Assertions.assertEquals(2, result.size());
		Assertions.assertFalse(result.stream().map(StrictProfile::strict).reduce(Boolean::logicalOr).orElse(true));
	}

	@Test
	void testAllStrict() {
		LinkedHashSet<String> profiles = new LinkedHashSet<>();
		profiles.add("a");
		profiles.add("b");
		LinkedHashSet<StrictProfile> result = StrictProfile.allStrict(profiles);

		Assertions.assertEquals(2, result.size());
		Assertions.assertTrue(result.stream().map(StrictProfile::strict).reduce(Boolean::logicalAnd).orElse(false));
	}

}
