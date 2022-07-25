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

import java.util.Iterator;
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

	@Test
	void testProfilesProvidedIncludeFalse() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a", "b");

		LinkedHashSet<String> profiles = new LinkedHashSet<>();

		Set<StrictProfile> expected = ConfigUtils.profiles(false, profiles, environment);
		Assertions.assertEquals(0, expected.size());
	}

	/**
	 * <pre>
	 *     - env holds :      [a, b]
	 *     - strict-profiles: [a]
	 *
	 *     as a result : [a=strict, b=non-strict]
	 * </pre>
	 */
	@Test
	void testProfilesNotProvidedIncludeTrue() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a", "b");

		LinkedHashSet<String> profiles = new LinkedHashSet<>();

		LinkedHashSet<StrictProfile> expected = ConfigUtils.profiles(true, profiles, environment);
		Assertions.assertEquals(2, expected.size());

		Iterator<StrictProfile> iterator = expected.iterator();
		StrictProfile first = iterator.next();
		Assertions.assertEquals(first.name(), "a");
		Assertions.assertFalse(first.strict());

		StrictProfile second = iterator.next();
		Assertions.assertEquals(second.name(), "b");
		Assertions.assertFalse(second.strict());
	}

	/**
	 * <pre>
	 *     - env holds :      [a, b]
	 *     - strict-profiles: []
	 *
	 *     as a result : [a=non-strict, b=non-strict]
	 * </pre>
	 */
	@Test
	void testProfilesProvidedIncludeTrue() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a", "b");

		LinkedHashSet<String> profiles = new LinkedHashSet<>();

		Set<StrictProfile> expected = ConfigUtils.profiles(true, profiles, environment);
		Assertions.assertEquals(2, expected.size());
		Iterator<StrictProfile> iterator = expected.iterator();
		StrictProfile first = iterator.next();
		Assertions.assertEquals(first.name(), "a");
		Assertions.assertFalse(first.strict());

		StrictProfile second = iterator.next();
		Assertions.assertEquals(second.name(), "b");
		Assertions.assertFalse(second.strict());
	}

	/**
	 * <pre>
	 *     - env holds :      [a, b]
	 *     - strict-profiles: [a, d]
	 *
	 *     as a result : [a=strict, b=non-strict]
	 * </pre>
	 */
	@Test
	void testProfilesProvideUnExistentProfileIncludeTrue() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a", "b");

		LinkedHashSet<String> profiles = new LinkedHashSet<>();
		profiles.add("a");
		profiles.add("d");

		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> ConfigUtils.profiles(true, profiles, environment));

		Assertions.assertEquals(
				"profile : d is not an active profile, but there is source definition for it under 'strict-for-profiles'",
				ex.getMessage());
	}

	/**
	 * <pre>
	 *     - env holds :      [a, b, c, d]
	 *     - strict-profiles: [d, c]
	 *
	 *     as a result : [a=non-strict, b=non-strict, d=strict, c=strict]
	 * </pre>
	 */
	@Test
	void testProfilesCorrectOrder() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("a", "b", "c", "d");

		LinkedHashSet<String> profiles = new LinkedHashSet<>();
		profiles.add("d");
		profiles.add("c");

		Set<StrictProfile> expected = ConfigUtils.profiles(true, profiles, environment);
		Assertions.assertEquals(4, expected.size());

		Assertions.assertEquals(4, expected.size());
		Iterator<StrictProfile> iterator = expected.iterator();
		StrictProfile first = iterator.next();
		Assertions.assertEquals(first.name(), "a");
		Assertions.assertFalse(first.strict());

		StrictProfile second = iterator.next();
		Assertions.assertEquals(second.name(), "b");
		Assertions.assertFalse(second.strict());

		StrictProfile third = iterator.next();
		Assertions.assertEquals(third.name(), "d");
		Assertions.assertTrue(third.strict());

		StrictProfile fourth = iterator.next();
		Assertions.assertEquals(fourth.name(), "c");
		Assertions.assertTrue(fourth.strict());
	}

	/**
	 * <pre>
	 *     - strippedSources: {a, a-dev, a-prod}
	 *     - env profiles   : {dev}
	 *     - strictSources  : {[a, true], [a-dev, true], [a-k8s, false]}
	 *
	 *     As a result, "a" and "a-dev" are being picked up, while "a-k8s" is skipped and "a-prod" is ignored.
	 *     "{a, a-dev}" is the data we hold.
	 * </pre>
	 */
	@Test
	void testProcessNamedDataNoFailures() {

		StrippedSourceContainer a = new StrippedSourceContainer(null, "a", Map.of("a", "a"));
		StrippedSourceContainer aDev = new StrippedSourceContainer(null, "a-dev", Map.of("a", "a-dev"));
		StrippedSourceContainer aProd = new StrippedSourceContainer(null, "a-prod", Map.of("a", "a-prod"));
		List<StrippedSourceContainer> strippedSources = List.of(a, aDev, aProd);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");
		environment.setProperty("spring.application.name", "no-failure");

		StrictSource aStrict = new StrictSource("a", true);
		StrictSource aDevStrict = new StrictSource("a-dev", true);
		StrictSource aK8sStrict = new StrictSource("a-k8s", false);
		LinkedHashSet<StrictSource> strictSources = Stream.of(aStrict, aDevStrict, aK8sStrict)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		MultipleSourcesContainer result = ConfigUtils.processNamedData(strippedSources, environment, strictSources,
				"default", false);
		Assertions.assertNotNull(result);

		LinkedHashSet<String> names = result.names();
		Iterator<String> iterator = names.iterator();
		String first = iterator.next();
		Assertions.assertEquals("a", first);
		String second = iterator.next();
		Assertions.assertEquals("a-dev", second);

		Map<String, Object> data = result.data();
		Assertions.assertEquals(1, data.size());
		Assertions.assertEquals("a-dev", data.get("a"));
	}

	/**
	 * <pre>
	 *     - strippedSources: {a, a-dev, a-prod}
	 *     - env profiles   : {dev}
	 *     - strictSources  : {[a, true], [a-dev, true], [a-k8s, true]}
	 *
	 *     As a result, we fail, because "a-k8s" is mandatory, but it is not found within strippedSources
	 * </pre>
	 */
	@Test
	void testProcessNamedDataFailure() {

		StrippedSourceContainer a = new StrippedSourceContainer(null, "a", Map.of("a", "a"));
		StrippedSourceContainer aDev = new StrippedSourceContainer(null, "a-dev", Map.of("a", "a-dev"));
		StrippedSourceContainer aProd = new StrippedSourceContainer(null, "a-prod", Map.of("a", "a-prod"));
		List<StrippedSourceContainer> strippedSources = List.of(a, aDev, aProd);

		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");
		environment.setProperty("spring.application.name", "no-failure");

		StrictSource aStrict = new StrictSource("a", true);
		StrictSource aDevStrict = new StrictSource("a-dev", true);
		StrictSource aK8sStrict = new StrictSource("a-k8s", true);
		LinkedHashSet<StrictSource> strictSources = Stream.of(aStrict, aDevStrict, aK8sStrict)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> ConfigUtils.processNamedData(strippedSources, environment, strictSources, "default", false));

		Assertions.assertEquals("source : a-k8s not present in namespace: default", ex.getMessage());
	}

	/**
	 * <pre>
	 *     - strippedSources: {a}
	 *     - strict: true
	 *     - labels: {key=value}
	 *
	 *     As a result, we fail, because such a source is not found.
	 * </pre>
	 */
	@Test
	void testProcessLabelDataFailureByLabels() {
		StrippedSourceContainer a = new StrippedSourceContainer(null, "a", Map.of("a", "a"));
		List<StrippedSourceContainer> strippedSources = List.of(a);

		MockEnvironment environment = new MockEnvironment();
		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();

		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> ConfigUtils.processLabeledData(strippedSources, environment, Map.of("key", "value"), "default",
						strictProfiles, false, true));

		Assertions.assertEquals("source(s) with labels : {key=value} not present in namespace: default",
				ex.getMessage());
	}

	/**
	 * <pre>
	 *     - strippedSources: "a" with labels "color: red"
	 *     - strictProfiles: [{"dev", false}, {"prod", true}]
	 *     - strict: true
	 *     - labels: {color=red}
	 *
	 *     We have a source called "a" with labels "color: red". Also two profiles are enabled:
	 *     "dev" that is not strict and "prod", that is strict.
	 *
	 *     We search by labels "color: red" and while "a-dev" is ignored since it is not found,
	 *     "a-prod" causes a failure since it is strict/mandatory.
	 *
	 * </pre>
	 */
	@Test
	void testProcessLabelDataFailureByProfile() {
		StrippedSourceContainer a = new StrippedSourceContainer(Map.of("color", "red"), "a", Map.of("a", "a"));
		List<StrippedSourceContainer> strippedSources = List.of(a);

		MockEnvironment environment = new MockEnvironment();
		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();
		strictProfiles.add(new StrictProfile("dev", false));
		strictProfiles.add(new StrictProfile("prod", true));

		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> ConfigUtils.processLabeledData(strippedSources, environment, Map.of("color", "red"), "default",
						strictProfiles, false, true));

		Assertions.assertEquals("source : a-prod not present in namespace: default", ex.getMessage());
	}

	/**
	 * <pre>
	 *     - strippedSources: {["a" with labels "color: red"], ["a-dev" with labels "color-dev: red-dev"],
	 *                        ["a-prod" with labels "color-prod: red-prod"], ["a-xx" with labels "color-xxx, "red-xx"]
	 *     - strictProfiles: [{"dev", true}, {"prod", true}]
	 *     - strict: true
	 *     - labels: {color=red}
	 *
	 *     We have a source called "a" with labels "color: red". Also two profiles are enabled:
	 *     "dev" and "prod", both are strict/mandatory
	 *
	 *     We search by labels "color: red" find source "a", but also find "a-dev" and "a-prod".
	 *     "a-xx" although present in the namespaces is not taken into account, since "xx" is not an active profile.
	 *
	 * </pre>
	 */
	@Test
	void testProcessLabelDataNoFailure() {
		StrippedSourceContainer a = new StrippedSourceContainer(Map.of("color", "red"), "a", Map.of("a", "a"));
		StrippedSourceContainer aDev = new StrippedSourceContainer(Map.of("color-dev", "red-dev"), "a-dev",
				Map.of("a", "a-dev"));
		StrippedSourceContainer aProd = new StrippedSourceContainer(Map.of("color-prod", "red-prod"), "a-prod",
				Map.of("a", "a-prod"));
		StrippedSourceContainer aXX = new StrippedSourceContainer(Map.of("color-xx", "red-xx"), "a-xx",
				Map.of("a", "a-xx"));
		List<StrippedSourceContainer> strippedSources = List.of(a, aDev, aProd, aXX);

		MockEnvironment environment = new MockEnvironment();
		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();
		strictProfiles.add(new StrictProfile("dev", true));
		strictProfiles.add(new StrictProfile("prod", true));

		MultipleSourcesContainer result = ConfigUtils.processLabeledData(strippedSources, environment,
				Map.of("color", "red"), "default", strictProfiles, false, true);

		Assertions.assertNotNull(result);
		Iterator<String> names = result.names().iterator();
		Assertions.assertEquals("a", names.next());
		Assertions.assertEquals("a-dev", names.next());
		Assertions.assertEquals("a-prod", names.next());

		Assertions.assertEquals("a-prod", result.data().get("a"));
	}

	/**
	 * failFast=true and exception is of type StrictSourceNotFoundException
	 */
	@Test
	void onExceptionFailFastTrueStrictException() {
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> ConfigUtils.onException(true, new StrictSourceNotFoundException("just because")));

		Assertions.assertEquals("just because", ex.getMessage());
	}

	/**
	 * failFast=true and exception is of type RuntimeException
	 */
	@Test
	void onExceptionFailFastTrueRuntimeException() {
		IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
				() -> ConfigUtils.onException(true, new RuntimeException("just because")));

		Assertions.assertEquals("just because", ex.getMessage());
	}

	/**
	 * failFast=false and exception is of type StrictSourceNotFoundException
	 */
	@Test
	void onExceptionFailFastFalseStrictException() {
		StrictSourceNotFoundException ex = Assertions.assertThrows(StrictSourceNotFoundException.class,
				() -> ConfigUtils.onException(false, new StrictSourceNotFoundException("just because")));

		Assertions.assertEquals("just because", ex.getMessage());
	}

	/**
	 * failFast=false and exception is of type RuntimeException
	 */
	@Test
	void onExceptionFailFastFalseRuntimeException() {
		Assertions.assertDoesNotThrow(() -> ConfigUtils.onException(false, new RuntimeException("just because")));
	}

	/**
	 * <pre>
	 *     - StrictProfiles : {[dev, false], [prod, true]}
	 *     - byLabels: {a-dev, b-prod, c}
	 * </pre>
	 */
	@Test
	void testRootSourcesOne() {

		List<StrippedSourceContainer> byLabels = List.of(
			new StrippedSourceContainer(Map.of("a", "b"), "a-dev", Map.of()),
			new StrippedSourceContainer(Map.of("a", "b"), "b-prod", Map.of()),
			new StrippedSourceContainer(Map.of("a", "b"), "c", Map.of())
		);

		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();
		strictProfiles.add(new StrictProfile("dev", false));
		strictProfiles.add(new StrictProfile("prod", true));

		List<StrippedSourceContainer> result = ConfigUtils.rootSources(byLabels, strictProfiles);
		Assertions.assertEquals(1, result.size());
		Assertions.assertEquals("c", result.get(0).name());
	}

	/**
	 * <pre>
	 *     - StrictProfiles : {[dev, false], [prod, true]}
	 *     - byLabels: {a-dev, b-prod}
	 * </pre>
	 */
	@Test
	void testRootSourcesTwo() {

		List<StrippedSourceContainer> byLabels = List.of(
			new StrippedSourceContainer(Map.of("a", "b"), "a-dev", Map.of()),
			new StrippedSourceContainer(Map.of("a", "b"), "b-prod", Map.of())
		);

		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();
		strictProfiles.add(new StrictProfile("dev", false));
		strictProfiles.add(new StrictProfile("prod", true));

		List<StrippedSourceContainer> result = ConfigUtils.rootSources(byLabels, strictProfiles);
		Assertions.assertEquals(0, result.size());
	}

	/**
	 * <pre>
	 *     - rootSources: {[a, b, c]}
	 *     - StrictProfiles : {[dev, false], [prod, true]}
	 *     - byLabels: {a-dev, b-prod}
	 * </pre>
	 */
	@Test
	void testSiblingsRootSourcesPresent() {

		List<StrippedSourceContainer> rootSources = List.of(
			new StrippedSourceContainer(Map.of("key", "value"), "a", Map.of()),
			new StrippedSourceContainer(Map.of("key", "value"), "b", Map.of()),
			new StrippedSourceContainer(Map.of("key", "value"), "c", Map.of())
		);

		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();
		strictProfiles.add(new StrictProfile("dev", false));
		strictProfiles.add(new StrictProfile("prod", true));

		List<StrictSource> result = ConfigUtils.siblings(rootSources, strictProfiles, List.of());
		Assertions.assertEquals(6, result.size());
		Assertions.assertEquals("a-dev", result.get(0).name());
		Assertions.assertEquals("b-dev", result.get(1).name());
		Assertions.assertEquals("c-dev", result.get(2).name());

		Assertions.assertEquals("a-prod", result.get(3).name());
		Assertions.assertEquals("b-prod", result.get(4).name());
		Assertions.assertEquals("c-prod", result.get(5).name());
	}

	/**
	 * <pre>
	 *     - rootSources: {}
	 *     - StrictProfiles : {[dev, false], [prod, true]}
	 *     - byLabels: {a-dev, b-prod, c-k8s}
	 * </pre>
	 */
	@Test
	void testSiblingsRootSourcesNotPresent() {

		List<StrippedSourceContainer> byLabels = List.of(
			new StrippedSourceContainer(Map.of("key", "value"), "a-dev", Map.of()),
			new StrippedSourceContainer(Map.of("key", "value"), "b-prod", Map.of()),
			new StrippedSourceContainer(Map.of("key", "value"), "c-k8s", Map.of())
		);

		LinkedHashSet<StrictProfile> strictProfiles = new LinkedHashSet<>();
		strictProfiles.add(new StrictProfile("dev", false));
		strictProfiles.add(new StrictProfile("prod", true));

		// c-k8s is not present, simply because k8s is not a profile that we have currently active
		List<StrictSource> result = ConfigUtils.siblings(List.of(), strictProfiles, byLabels);
		Assertions.assertEquals(2, result.size());
		Assertions.assertEquals("a-dev", result.get(0).name());
		Assertions.assertEquals("b-prod", result.get(1).name());

	}

}
