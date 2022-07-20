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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class LabeledSecretNormalizedSourceTests {

	private final Map<String, String> labels = Collections.singletonMap("a", "b");

	@Test
	void testEqualsAndHashCode() {
		Set<StrictProfile> leftProfiles = Set.of(new StrictProfile("left", false));
		Set<StrictProfile> rightProfiles = Set.of(new StrictProfile("right", true));

		LabeledSecretNormalizedSource left = new LabeledSecretNormalizedSource("namespace", labels, false, leftProfiles,
				false);
		LabeledSecretNormalizedSource right = new LabeledSecretNormalizedSource("namespace", labels, false,
				rightProfiles, true);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	/*
	 * left and right instance have a different prefix, but the hashCode is the same; as
	 * it is not taken into consideration when computing hashCode
	 */
	@Test
	void testEqualsAndHashCodePrefixDoesNotMatter() {

		Set<StrictProfile> leftProfiles = Set.of(new StrictProfile("left", false));
		Set<StrictProfile> rightProfiles = Set.of(new StrictProfile("right", true));

		ConfigUtils.Prefix knownLeft = ConfigUtils.findPrefix("left", false, false, "some");
		ConfigUtils.Prefix knownRight = ConfigUtils.findPrefix("right", false, false, "some");

		LabeledSecretNormalizedSource left = new LabeledSecretNormalizedSource("namespace", labels, true, knownLeft,
				leftProfiles, false);
		LabeledSecretNormalizedSource right = new LabeledSecretNormalizedSource("namespace", labels, true, knownRight,
				rightProfiles, false);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		Set<StrictProfile> profiles = Set.of(new StrictProfile("profile", false));
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, profiles,
				false);
		Assertions.assertSame(source.type(), NormalizedSourceType.LABELED_SECRET);
	}

	@Test
	void testImmutableGetLabels() {
		Set<StrictProfile> profiles = Set.of(new StrictProfile("profile", false));
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, profiles,
				false);
		Assertions.assertThrows(RuntimeException.class, () -> source.labels().put("c", "d"));
	}

	@Test
	void testTarget() {
		Set<StrictProfile> profiles = Set.of(new StrictProfile("profile", false));
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, profiles,
				false);
		Assertions.assertEquals(source.target(), "secret");
	}

	@Test
	void testConstructorFields() {
		Set<StrictProfile> profiles = Set.of(new StrictProfile("profile", false));
		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("prefix", false, false, "some");
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, prefix,
				profiles, true);
		Assertions.assertTrue(source.name().isEmpty());
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertFalse(source.failFast());
		Assertions.assertTrue(source.strict());
		Assertions.assertEquals(source.profiles().iterator().next().name(), "profile");
		Assertions.assertFalse(source.profiles().iterator().next().strict());
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		Set<StrictProfile> profiles = Set.of(new StrictProfile("profile", false));
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, true, profiles,
				true);
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertTrue(source.failFast());
		Assertions.assertSame(ConfigUtils.Prefix.DEFAULT, source.prefix());
		Assertions.assertEquals(source.profiles().iterator().next().name(), "profile");
		Assertions.assertFalse(source.profiles().iterator().next().strict());
	}

}
