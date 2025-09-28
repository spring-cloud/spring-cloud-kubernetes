/*
 * Copyright 2013-present the original author or authors.
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class LabeledSecretNormalizedSourceTests {

	private final Map<String, String> labels = Collections.singletonMap("a", "b");

	@Test
	void testEqualsAndHashCode() {
		LabeledSecretNormalizedSource left = new LabeledSecretNormalizedSource("namespace", labels, false,
				ConfigUtils.Prefix.DEFAULT);
		LabeledSecretNormalizedSource right = new LabeledSecretNormalizedSource("namespace", labels, true,
				ConfigUtils.Prefix.DEFAULT);

		Assertions.assertThat(left.hashCode()).isEqualTo(right.hashCode());
		Assertions.assertThat(left).isEqualTo(right);
	}

	/*
	 * left and right instance have a different prefix, but the hashCode is the same; as
	 * it is not taken into consideration when computing hashCode
	 */
	@Test
	void testEqualsAndHashCodePrefixDoesNotMatter() {

		ConfigUtils.Prefix knownLeft = ConfigUtils.findPrefix("left", false, false, "some");
		ConfigUtils.Prefix knownRight = ConfigUtils.findPrefix("right", false, false, "some");

		LabeledSecretNormalizedSource left = new LabeledSecretNormalizedSource("namespace", labels, true, knownLeft);
		LabeledSecretNormalizedSource right = new LabeledSecretNormalizedSource("namespace", labels, true, knownRight);

		Assertions.assertThat(left.hashCode()).isEqualTo(right.hashCode());
		Assertions.assertThat(left).isEqualTo(right);
	}

	@Test
	void testType() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false,
				ConfigUtils.Prefix.DEFAULT);
		Assertions.assertThat(source.type()).isSameAs(NormalizedSourceType.LABELED_SECRET);
	}

	@Test
	void testImmutableGetLabels() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false,
				ConfigUtils.Prefix.DEFAULT);
		Assertions.assertThatThrownBy(() -> source.labels().put("c", "d")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void testTarget() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false,
				ConfigUtils.Prefix.DEFAULT);
		Assertions.assertThat(source.target()).isEqualTo("secret");
	}

	@Test
	void testConstructorFields() {
		ConfigUtils.Prefix prefix = ConfigUtils.findPrefix("prefix", false, false, "some");
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, false, prefix);
		Assertions.assertThat(source.name().isEmpty()).isTrue();
		Assertions.assertThat(source.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(source.failFast()).isFalse();
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		LabeledSecretNormalizedSource source = new LabeledSecretNormalizedSource("namespace", labels, true,
				ConfigUtils.Prefix.DEFAULT);
		Assertions.assertThat(source.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(source.failFast()).isTrue();
		Assertions.assertThat(ConfigUtils.Prefix.DEFAULT).isSameAs(source.prefix());
	}

}
