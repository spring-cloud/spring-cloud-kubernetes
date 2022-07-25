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

import java.util.LinkedHashSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class NamedSecretNormalizedSourceTests {

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "prefix");

	@Test
	void testEqualsAndHashCode() {

		LinkedHashSet<StrictProfile> leftProfiles = new LinkedHashSet<>();
		leftProfiles.add(new StrictProfile("left", false));
		LinkedHashSet<StrictProfile> rightProfiles = new LinkedHashSet<>();
		rightProfiles.add(new StrictProfile("right", false));

		NamedSecretNormalizedSource left = new NamedSecretNormalizedSource("name", "namespace", false, leftProfiles,
				false);
		NamedSecretNormalizedSource right = new NamedSecretNormalizedSource("name", "namespace", true, rightProfiles,
				true);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, profiles,
				false);
		Assertions.assertSame(source.type(), NormalizedSourceType.NAMED_SECRET);
	}

	@Test
	void testTarget() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, profiles,
				false);
		Assertions.assertEquals(source.target(), "secret");
	}

	@Test
	void testConstructorFields() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, PREFIX,
				profiles, true);
		Assertions.assertEquals(source.name().get(), "name");
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertFalse(source.failFast());
		Assertions.assertSame(PREFIX, source.prefix());
		Assertions.assertEquals(source.profiles().iterator().next().name(), "profile");
		Assertions.assertFalse(source.profiles().iterator().next().strict());
		Assertions.assertTrue(source.strict());
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", true, profiles, true);
		Assertions.assertEquals(source.name().get(), "name");
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertTrue(source.failFast());
		Assertions.assertSame(ConfigUtils.Prefix.DEFAULT, source.prefix());
		Assertions.assertEquals(source.profiles().iterator().next().name(), "profile");
		Assertions.assertFalse(source.profiles().iterator().next().strict());
		Assertions.assertTrue(source.strict());
	}

}
