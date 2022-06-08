/*
 * Copyright 2013-2021 the original author or authors.
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
class NamedSecretNormalizedSourceTests {

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "prefix");

	@Test
	void testEqualsAndHashCode() {
		NamedSecretNormalizedSource left = new NamedSecretNormalizedSource("name", "namespace", false, false);
		NamedSecretNormalizedSource right = new NamedSecretNormalizedSource("name", "namespace", true, false);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, false);
		Assertions.assertSame(source.type(), NormalizedSourceType.NAMED_SECRET);
	}

	@Test
	void testTarget() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, false);
		Assertions.assertEquals(source.target(), "secret");
	}

	@Test
	void testConstructorFields() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, PREFIX, true);
		Assertions.assertEquals(source.name().get(), "name");
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertFalse(source.failFast());
		Assertions.assertSame(PREFIX, source.prefix());
		Assertions.assertTrue(source.profileSpecificSources());
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", true, true);
		Assertions.assertEquals(source.name().get(), "name");
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertTrue(source.failFast());
		Assertions.assertSame(ConfigUtils.Prefix.DEFAULT, source.prefix());
		Assertions.assertTrue(source.profileSpecificSources());
	}

}
