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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class NamedConfigMapNormalizedSourceTests {

	@Test
	void testEqualsAndHashCode() {
		NamedConfigMapNormalizedSource left = new NamedConfigMapNormalizedSource("name", "namespace", false, "prefix",
				true);
		NamedConfigMapNormalizedSource right = new NamedConfigMapNormalizedSource("name", "namespace", true,
				"non-equal-prefix", false);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, "prefix",
				true);
		Assertions.assertSame(one.type(), NormalizedSourceType.NAMED_CONFIG_MAP);
	}

	@Test
	void testTarget() {
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, "prefix",
				true);
		Assertions.assertEquals(one.target(), "Config Map");
	}

	@Test
	void testConstructorFields() {
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, "prefix",
				true);
		Assertions.assertEquals(one.name(), "name");
		Assertions.assertEquals(one.namespace(), "namespace");
		Assertions.assertFalse(one.failFast());
	}

}
