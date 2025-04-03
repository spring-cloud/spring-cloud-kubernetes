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

import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class LabeledConfigMapNormalizedSourceTests {

	private static final ConfigUtils.Prefix PREFIX = ConfigUtils.findPrefix("prefix", false, false, "prefix");

	@Test
	void testEqualsAndHashCode() {
		LabeledConfigMapNormalizedSource left = new LabeledConfigMapNormalizedSource("name", Map.of("key", "value"),
				false, false);
		LabeledConfigMapNormalizedSource right = new LabeledConfigMapNormalizedSource("name", Map.of("key", "value"),
				true, false);

		Assertions.assertThat(left.hashCode()).isEqualTo(right.hashCode());
		Assertions.assertThat(left).isEqualTo(right);
	}

	@Test
	void testType() {
		LabeledConfigMapNormalizedSource source = new LabeledConfigMapNormalizedSource("name", Map.of("key", "value"),
				false, false);
		Assertions.assertThat(source.type()).isSameAs(NormalizedSourceType.LABELED_CONFIG_MAP);
	}

	@Test
	void testTarget() {
		LabeledConfigMapNormalizedSource source = new LabeledConfigMapNormalizedSource("name", Map.of("key", "value"),
				false, false);
		Assertions.assertThat(source.target()).isEqualTo("configmap");
	}

	@Test
	void testConstructorFields() {
		LabeledConfigMapNormalizedSource source = new LabeledConfigMapNormalizedSource("namespace",
				Map.of("key", "value"), false, PREFIX, true);
		Assertions.assertThat(source.labels()).containsExactlyInAnyOrderEntriesOf(Map.of("key", "value"));
		Assertions.assertThat(source.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(source.failFast()).isFalse();
		Assertions.assertThat(PREFIX).isSameAs(source.prefix());
		Assertions.assertThat(source.profileSpecificSources()).isTrue();
	}

	@Test
	void testConstructorWithoutPrefixFields() {
		LabeledConfigMapNormalizedSource source = new LabeledConfigMapNormalizedSource("namespace",
				Map.of("key", "value"), true, true);
		Assertions.assertThat(source.labels()).containsExactlyInAnyOrderEntriesOf(Map.of("key", "value"));
		Assertions.assertThat(source.namespace().get()).isEqualTo("namespace");
		Assertions.assertThat(source.failFast()).isTrue();
		Assertions.assertThat(ConfigUtils.Prefix.DEFAULT).isSameAs(source.prefix());
		Assertions.assertThat(source.profileSpecificSources()).isTrue();
	}

}
