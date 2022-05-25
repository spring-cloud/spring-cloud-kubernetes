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

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
		Assertions.assertSame(result, ConfigUtils.Prefix.UNSET);
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
		Assertions.assertSame(result, ConfigUtils.Prefix.UNSET);
	}

	@Test
	void testUnsetException() {
		ConfigUtils.Prefix result = ConfigUtils.findPrefix("", null, false, "name-not-to-use");
		Assertions.assertSame(result, ConfigUtils.Prefix.UNSET);

		IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
				() -> result.prefixProvider().get());

		Assertions.assertEquals("prefix is unset, nothing to provide", ex.getMessage());
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

		Assertions.assertEquals(result.sourceName().length(), 31);
		Assertions.assertTrue(result.sourceName().contains("name2"));
		Assertions.assertTrue(result.sourceName().contains("name1"));
		Assertions.assertTrue(result.sourceName().contains("configmap"));
		Assertions.assertTrue(result.sourceName().contains("namespace"));

		Assertions.assertEquals(result.sourceData().get("prefix.a"), "b");
		Assertions.assertEquals(result.sourceData().get("prefix.c"), "d");
	}

}
