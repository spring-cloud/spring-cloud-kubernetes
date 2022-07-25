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
class NamedConfigMapNormalizedSourceTests {

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "prefix");

	@Test
	void testEqualsAndHashCode() {

		ConfigUtils.Prefix knownLeft = ConfigUtils.findPrefix("prefix", false, false, "some");
		ConfigUtils.Prefix knownRight = ConfigUtils.findPrefix("prefix", false, false, "non-equal-prefix");

		LinkedHashSet<StrictProfile> leftProfiles = new LinkedHashSet<>();
		leftProfiles.add(new StrictProfile("left", false));

		LinkedHashSet<StrictProfile> rightProfiles = new LinkedHashSet<>();
		rightProfiles.add(new StrictProfile("right", false));

		NamedConfigMapNormalizedSource left = new NamedConfigMapNormalizedSource("name", "namespace", false, knownLeft,
				leftProfiles, false);
		NamedConfigMapNormalizedSource right = new NamedConfigMapNormalizedSource("name", "namespace", true, knownRight,
				rightProfiles, true);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, PREFIX,
				profiles, false);
		Assertions.assertSame(one.type(), NormalizedSourceType.NAMED_CONFIG_MAP);
	}

	@Test
	void testTarget() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, PREFIX,
				profiles, false);
		Assertions.assertEquals(one.target(), "configmap");
	}

	@Test
	void testConstructorFields() {
		LinkedHashSet<StrictProfile> profiles = new LinkedHashSet<>();
		profiles.add(new StrictProfile("profile", false));
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, PREFIX,
				profiles, true);
		Assertions.assertEquals(one.name().get(), "name");
		Assertions.assertEquals(one.namespace().get(), "namespace");
		Assertions.assertFalse(one.failFast());
		Assertions.assertTrue(one.strict());
		Assertions.assertEquals(one.profiles().iterator().next().name(), "profile");
		Assertions.assertFalse(one.profiles().iterator().next().strict());
	}

}
