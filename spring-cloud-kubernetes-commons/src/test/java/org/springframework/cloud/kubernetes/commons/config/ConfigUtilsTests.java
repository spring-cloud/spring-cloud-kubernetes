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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class ConfigUtilsTests {

	@Test
	void testExplicitPrefixSet() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("explicitPrefix", null, false, "irrelevant");
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.KNOWN);
		Assertions.assertThat(result.prefixProvider().get()).isEqualTo("explicitPrefix");
	}

	@Test
	void testUseNameAsPrefixTrue() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", Boolean.TRUE, false, "name-to-use");
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.KNOWN);
		Assertions.assertThat(result.prefixProvider().get()).isEqualTo("name-to-use");
	}

	@Test
	void testUseNameAsPrefixFalse() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", Boolean.FALSE, false, "name-not-to-use");
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.DEFAULT);
	}

	@Test
	void testDefaultUseNameAsPrefixTrue() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, true, "name-to-use");
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.KNOWN);
		Assertions.assertThat(result.prefixProvider().get()).isEqualTo("name-to-use");
	}

	@Test
	void testNoMatch() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, false, "name-not-to-use");
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.DEFAULT);
	}

	@Test
	void testUnsetEmpty() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, false, "name-not-to-use");
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.DEFAULT);

		String[] expected = new String[1];
		Assertions.assertThatCode(() -> expected[0] = result.prefixProvider().get()).doesNotThrowAnyException();
		Assertions.assertThat(expected[0]).isEmpty();
	}

	@Test
	void testDelayed() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix(null, true, false, null);
		Assertions.assertThat(result).isSameAs(ConfigUtils.Prefix.DELAYED);

		Assertions.assertThatThrownBy(() -> result.prefixProvider().get())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("prefix is delayed, needs to be taken elsewhere");

	}

	/**
	 * <pre>
	 *   spring:
	 *     cloud:
	 *        kubernetes:
	 *           config:
	 *              includeProfileSpecificSources: true
	 * </pre>
	 *
	 * above will generate "true" for a normalized source
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesOnlyDefaultSet() {
		Assertions.assertThat(ConfigUtils.includeProfileSpecificSources(true, null)).isTrue();
	}

	/**
	 * <pre>
	 *   spring:
	 *     cloud:
	 *        kubernetes:
	 *           config:
	 *              includeProfileSpecificSources: true
	 * </pre>
	 *
	 * above will generate "false" for a normalized source
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesOnlyDefaultNotSet() {
		Assertions.assertThat(ConfigUtils.includeProfileSpecificSources(false, null)).isFalse();
	}

	/**
	 * <pre>
	 *   spring:
	 *     cloud:
	 *        kubernetes:
	 *           config:
	 *              includeProfileSpecificSources: true
	 *           sources:
	 *           - name: one
	 *             includeProfileSpecificSources: false
	 * </pre>
	 *
	 * above will generate "false" for a normalized source
	 */
	@Test
	void testUseIncludeProfileSpecificSourcesSourcesOverridesDefault() {
		Assertions.assertThat(ConfigUtils.includeProfileSpecificSources(true, false)).isFalse();
	}

	@Test
	void testWithPrefix() {
		PrefixContext context = new PrefixContext(Map.of("a", "b", "c", "d"), "prefix", "namespace",
				Set.of("name1", "name2"));

		SourceData result = ConfigUtils.withPrefix("configmap", context);

		Assertions.assertThat(result.sourceName()).isEqualTo("configmap.name1.name2.namespace");

		Assertions.assertThat(result.sourceData().get("prefix.a")).isEqualTo("b");
		Assertions.assertThat(result.sourceData().get("prefix.c")).isEqualTo("d");
	}

	/*
	 * source names should be reproducible all the time, this test asserts this.
	 */
	@Test
	void testWithPrefixSortedName() {
		PrefixContext context = new PrefixContext(Map.of("a", "b", "c", "d"), "prefix", "namespace",
				Set.of("namec", "namea", "nameb"));

		SourceData result = ConfigUtils.withPrefix("configmap", context);
		Assertions.assertThat(result.sourceName()).isEqualTo("configmap.namea.nameb.namec.namespace");

		Assertions.assertThat(result.sourceData().get("prefix.a")).isEqualTo("b");
		Assertions.assertThat(result.sourceData().get("prefix.c")).isEqualTo("d");
	}

	/**
	 * <pre>
	 *
	 *     - we have configmap-one with an application.yaml with two properties propA = A, prop = B
	 *     - we have configmap-one-kubernetes with an application.yaml with two properties propA = AA, probC = C
	 *
	 *     As a result we should get three properties as output.
	 *
	 * </pre>
	 */
	@Test
	void testMerge() {

		StrippedSourceContainer configMapOne = new StrippedSourceContainer(Map.of(), "configmap-one",
				Map.of(Constants.APPLICATION_YAML, "propA: A\npropB: B"));

		StrippedSourceContainer configMapOneK8s = new StrippedSourceContainer(Map.of(), "configmap-one-kubernetes",
				Map.of(Constants.APPLICATION_YAML, "propA: AA\npropC: C"));

		LinkedHashSet<String> sourceNames = Stream.of("configmap-one", "configmap-one-kubernetes")
			.collect(Collectors.toCollection(LinkedHashSet::new));

		MultipleSourcesContainer result = ConfigUtils.processNamedData(List.of(configMapOne, configMapOneK8s),
				new MockEnvironment(), sourceNames, "default", false);

		Assertions.assertThat(result.data().size()).isEqualTo(3);
		Assertions.assertThat(result.data().get("propA")).isEqualTo("AA");
		Assertions.assertThat(result.data().get("propB")).isEqualTo("B");
		Assertions.assertThat(result.data().get("propC")).isEqualTo("C");
	}

	@Test
	void testKeysWithPrefixNullMap() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(null, "");
		Assertions.assertThat(result.isEmpty()).isTrue();
	}

	@Test
	void testKeysWithPrefixEmptyMap() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(Map.of(), "");
		Assertions.assertThat(result.isEmpty()).isTrue();
	}

	@Test
	void testKeysWithPrefixEmptyPrefix() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(Map.of("a", "b"), "");
		Assertions.assertThat(result.isEmpty()).isFalse();
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("a", "b"));
	}

	@Test
	void testKeysWithPrefixNonEmptyPrefix() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(Map.of("a", "b", "c", "d"), "prefix-");
		Assertions.assertThat(result.isEmpty()).isFalse();
		Assertions.assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("prefix-a", "b", "prefix-c", "d"));
	}

}
