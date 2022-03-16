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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class LabeledSecretNormalizedSourceTests {

	private final Map<String, String> labels = Collections.singletonMap("a", "b");

	private static final String PREFIX = "prefix";

	@Test
	void testEqualsAndHashCode() {
		LabeledSecretNormalizedSource left = new LabeledSecretNormalizedSource("namespace", labels, false, PREFIX,
				false);
		LabeledSecretNormalizedSource right = new LabeledSecretNormalizedSource("namespace", labels, true, PREFIX,
				false);

		Assertions.assertEquals(left.hashCode(), right.hashCode());
		Assertions.assertEquals(left, right);
	}

	@Test
	void testType() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, PREFIX,
				false);
		Assertions.assertSame(source.type(), NormalizedSourceType.LABELED_SECRET);
	}

	@Test
	void testImmutableGetLabels() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, PREFIX,
				false);
		Assertions.assertThrows(RuntimeException.class, () -> source.labels().put("c", "d"));
	}

	@Test
	void testTarget() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, PREFIX,
				false);
		Assertions.assertEquals(source.target(), "Secret");
	}

	@Test
	void testConstructorFields() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, PREFIX,
				false);
		Assertions.assertTrue(source.name().isEmpty());
		Assertions.assertEquals(source.namespace().get(), "namespace");
		Assertions.assertFalse(source.failFast());
		Assertions.assertEquals("prefix", source.prefix());
		Assertions.assertFalse(source.profileSpecificSources());
	}

}
