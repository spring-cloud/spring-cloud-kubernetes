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

import org.assertj.core.api.Assertions;
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

		NamedConfigMapNormalizedSource left = new NamedConfigMapNormalizedSource("name", "namespace", false, knownLeft,
				true);
		NamedConfigMapNormalizedSource right = new NamedConfigMapNormalizedSource("name", "namespace", true, knownRight,
				false);

		Assertions.assertThat(left.hashCode()).isEqualTo(right.hashCode());
		Assertions.assertThat(left).isEqualTo(right);
	}

	@Test
	void testType() {

		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, PREFIX,
				true);
		Assertions.assertThat(one.type()).isSameAs(NormalizedSourceType.NAMED_CONFIG_MAP);
	}

	@Test
	void testTarget() {
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, PREFIX,
				true);
		Assertions.assertThat(one.target()).isEqualTo("configmap");
	}

	@Test
	void testConstructorFields() {
		NamedConfigMapNormalizedSource one = new NamedConfigMapNormalizedSource("name", "namespace", false, PREFIX,
				true);
		Assertions.assertThat(one.name().get()).isEqualTo("name");
		Assertions.assertThat(one.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(one.failFast()).isFalse();
	}

}
