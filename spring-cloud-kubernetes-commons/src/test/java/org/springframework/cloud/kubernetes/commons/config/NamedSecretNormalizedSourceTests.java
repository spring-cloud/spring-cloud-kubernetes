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

import org.assertj.core.api.Assertions;
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

		Assertions.assertThat(left.hashCode()).isEqualTo(right.hashCode());
		Assertions.assertThat(left).isEqualTo(right);
	}

	@Test
	void testType() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, false);
		Assertions.assertThat(source.type()).isEqualTo(NormalizedSourceType.NAMED_SECRET);
	}

	@Test
	void testTarget() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, false);
		Assertions.assertThat(source.target()).isEqualTo("secret");
	}

	@Test
	void testConstructorFields() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", false, PREFIX, true);
		Assertions.assertThat(source.name().get()).isEqualTo("name");
		Assertions.assertThat(source.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(source.failFast()).isFalse();
		Assertions.assertThat(source.prefix()).isEqualTo(PREFIX);
		Assertions.assertThat(source.profileSpecificSources()).isTrue();
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		NamedSecretNormalizedSource source = new NamedSecretNormalizedSource("name", "namespace", true, true);
		Assertions.assertThat(source.name().get()).isEqualTo("name");
		Assertions.assertThat(source.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(source.failFast()).isFalse();
		Assertions.assertThat(ConfigUtils.Prefix.DEFAULT).isSameAs(source.prefix());
		Assertions.assertThat(source.profileSpecificSources()).isTrue();
	}

}
