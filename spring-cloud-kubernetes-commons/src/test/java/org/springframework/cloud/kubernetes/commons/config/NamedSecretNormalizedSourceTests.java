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

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class NamedSecretNormalizedSourceTests {

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "prefix");

	@Test
	void testEqualsAndHashCode() {

		Set<String> leftProfiles = Set.of("left");
		Set<String> rightProfiles = Set.of("right");

		NamedSecretNormalizedSource left = new NamedSecretNormalizedSource("name", "namespace", false, leftProfiles,
				false);
		NamedSecretNormalizedSource right = new NamedSecretNormalizedSource("name", "namespace", true, rightProfiles,
				true);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		Set<String> leftProfiles = Set.of("left");
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, leftProfiles,
				false);
		Assertions.assertSame(source.type(), NormalizedSourceType.NAMED_SECRET);
	}

	@Test
	void testTarget() {
		Set<String> leftProfiles = Set.of("left");
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, leftProfiles,
				false);
		Assertions.assertEquals(source.target(), "secret");
	}

	@Test
	void testConstructorFields() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, PREFIX,
				Set.of("a"), true);
		Assertions.assertEquals(source.name().get(), "name");
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertFalse(source.failFast());
		Assertions.assertSame(PREFIX, source.prefix());
		Assertions.assertEquals(source.profiles(), Set.of("a"));
		Assertions.assertTrue(source.strict());
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", true, Set.of("b"),
				true);
		Assertions.assertEquals(source.name().get(), "name");
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertTrue(source.failFast());
		Assertions.assertSame(ConfigUtils.Prefix.DEFAULT, source.prefix());
		Assertions.assertEquals(source.profiles(), Set.of("b"));
		Assertions.assertTrue(source.strict());
	}

}
