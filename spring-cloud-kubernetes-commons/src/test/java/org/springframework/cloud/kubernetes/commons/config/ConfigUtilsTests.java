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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

/**
 * @author wind57
 */
class ConfigUtilsTests {

	@Test
	void testExplicitPrefixSet() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("explicitPrefix", null, false, "irrelevant");
		Assertions.assertSame(result, ConfigUtils.Prefix.KNOWN);
		Assertions.assertEquals(result.prefixProvider().get(), "explicitPrefix");
	}

	@Test
	void testUseNameAsPrefixTrue() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", Boolean.TRUE, false, "name-to-use");
		Assertions.assertSame(result, ConfigUtils.Prefix.KNOWN);
		Assertions.assertEquals(result.prefixProvider().get(), "name-to-use");
	}

	@Test
	void testUseNameAsPrefixFalse() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", Boolean.FALSE, false, "name-not-to-use");
		Assertions.assertSame(result, ConfigUtils.Prefix.DEFAULT);
	}

	@Test
	void testDefaultUseNameAsPrefixTrue() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, true, "name-to-use");
		Assertions.assertSame(result, ConfigUtils.Prefix.KNOWN);
		Assertions.assertEquals(result.prefixProvider().get(), "name-to-use");
	}

	@Test
	void testNoMatch() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, false, "name-not-to-use");
		Assertions.assertSame(result, ConfigUtils.Prefix.DEFAULT);
	}

	@Test
	void testUnsetEmpty() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, false, "name-not-to-use");
		Assertions.assertSame(result, ConfigUtils.Prefix.DEFAULT);

		String expected = Assertions.assertDoesNotThrow(() -> result.prefixProvider().get());
		Assertions.assertEquals("", expected);
	}

	@Test
	void testDelayed() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix(null, true, false, null);
		Assertions.assertSame(result, ConfigUtils.Prefix.DELAYED);

		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
				() -> result.prefixProvider().get());

		Assertions.assertEquals("prefix is delayed, needs to be taken elsewhere", ex.getMessage());
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
		Assertions.assertTrue(ConfigUtils.includeProfileSpecificSources(true, null));
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
		Assertions.assertFalse(ConfigUtils.includeProfileSpecificSources(false, null));
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
		Assertions.assertFalse(ConfigUtils.includeProfileSpecificSources(true, false));
	}

	@Test
	void testWithPrefix() {
		PrefixContext context = new PrefixContext(Map.of("a", "b", "c", "d"), "prefix", "namespace",
				Set.of("name1", "name2"));

		SourceData result = ConfigUtils.withPrefix("configmap", context);

		Assertions.assertEquals(result.sourceName(), "configmap.name1.name2.namespace");

		Assertions.assertEquals(result.sourceData().get("prefix.a"), "b");
		Assertions.assertEquals(result.sourceData().get("prefix.c"), "d");
	}

	/*
	 * source names should be reproducible all the time, this test asserts this.
	 */
	@Test
	void testWithPrefixSortedName() {
		PrefixContext context = new PrefixContext(Map.of("a", "b", "c", "d"), "prefix", "namespace",
				Set.of("namec", "namea", "nameb"));

		SourceData result = ConfigUtils.withPrefix("configmap", context);
		Assertions.assertEquals(result.sourceName(), "configmap.namea.nameb.namec.namespace");

		Assertions.assertEquals(result.sourceData().get("prefix.a"), "b");
		Assertions.assertEquals(result.sourceData().get("prefix.c"), "d");
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
				Map.of("application.yaml", "propA: A\npropB: B"));

		StrippedSourceContainer configMapOneK8s = new StrippedSourceContainer(Map.of(), "configmap-one-kubernetes",
				Map.of("application.yaml", "propA: AA\npropC: C"));

		LinkedHashSet<String> sourceNames = Stream.of("configmap-one", "configmap-one-kubernetes")
				.collect(Collectors.toCollection(LinkedHashSet::new));

		MultipleSourcesContainer result = ConfigUtils.processNamedData(List.of(configMapOne, configMapOneK8s),
				new MockEnvironment(), sourceNames, "default", false);

		Assertions.assertEquals(result.data().size(), 3);
		Assertions.assertEquals(result.data().get("propA"), "AA");
		Assertions.assertEquals(result.data().get("propB"), "B");
		Assertions.assertEquals(result.data().get("propC"), "C");
	}

	@Test
	void testKeysWithPrefixNullMap() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(null, "");
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void testKeysWithPrefixEmptyMap() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(Map.of(), "");
		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	void testKeysWithPrefixEmptyPrefix() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(Map.of("a", "b"), "");
		Assertions.assertFalse(result.isEmpty());
		Assertions.assertEquals(Map.of("a", "b"), result);
	}

	@Test
	void testKeysWithPrefixNonEmptyPrefix() {
		Map<String, String> result = ConfigUtils.keysWithPrefix(Map.of("a", "b", "c", "d"), "prefix-");
		Assertions.assertFalse(result.isEmpty());
		Assertions.assertEquals(Map.of("prefix-a", "b", "prefix-c", "d"), result);
	}

}
