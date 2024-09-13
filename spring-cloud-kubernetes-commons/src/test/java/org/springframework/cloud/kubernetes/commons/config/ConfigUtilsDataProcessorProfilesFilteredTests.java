/*
 * Copyright 2013-2024 the original author or authors.
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtilsDataProcessor.profilesFiltered;

/**
 * Only test ConfigUtilsDataProcessor::profilesFiltered
 *
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class ConfigUtilsDataProcessorProfilesFilteredTests {

	/**
	 * <pre>
	 *     - profiles are empty
	 *     - allByLabels are empty
	 * </pre>
	 */
	@Test
	void profilesAreEmptyNoSources() {
		List<StrippedSourceContainer> sources = List.of();
		Set<String> profiles = Set.of();
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result).isEmpty();
	}

	/**
	 * <pre>
	 *     - profiles are empty
	 *     - first source present : 'one-dev'
	 *     - second source present: 'two-app-dev'
	 *     - third source present:  'three-my-app-k8s'
	 * </pre>
	 */
	@Test
	void profilesAreEmptyThreeSourcesNoMatch(CapturedOutput output) {
		List<StrippedSourceContainer> sources = List.of(new StrippedSourceContainer(Map.of(), "one-dev", Map.of()),
				new StrippedSourceContainer(Map.of(), "two-app-dev", Map.of()),
				new StrippedSourceContainer(Map.of(), "two-app-k8s", Map.of()));
		Set<String> profiles = Set.of();
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result).isEmpty();
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'one-dev' because it ends in : "
					+ "'dev' and we assume that is a profile name (and there are no active profiles)");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'two-app-dev' because "
					+ "it ends in : 'app-dev' and we assume that is a profile name (and there are no active profiles)");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'two-app-k8s' because "
					+ "it ends in : 'app-k8s' and we assume that is a profile name (and there are no active profiles)");
	}

	/**
	 * <pre>
	 *     - profiles are empty
	 *     - first source present : 'one'
	 *     - second source present: 'two'
	 * </pre>
	 */
	@Test
	void profilesAreEmptyTwoSourcesMatch(CapturedOutput output) {
		List<StrippedSourceContainer> sources = List.of(new StrippedSourceContainer(Map.of(), "one", Map.of()),
				new StrippedSourceContainer(Map.of(), "two", Map.of()));
		Set<String> profiles = Set.of();
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.get(0).name()).isEqualTo("one");
		Assertions.assertThat(result.get(1).name()).isEqualTo("two");
		Assertions.assertThat(output.getOut()).contains("taking source with name : 'one'");
		Assertions.assertThat(output.getOut()).contains("taking source with name : 'two'");
	}

	/**
	 * <pre>
	 *     - profiles are empty
	 *     - first source present : 'one-dev'
	 *     - second source present: 'one'
	 * </pre>
	 */
	@Test
	void profilesAreEmptyTwoSourcesOneMatches(CapturedOutput output) {
		List<StrippedSourceContainer> sources = List.of(new StrippedSourceContainer(Map.of(), "one", Map.of()),
				new StrippedSourceContainer(Map.of(), "one-dev", Map.of()));
		Set<String> profiles = Set.of();
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).name()).isEqualTo("one");
		Assertions.assertThat(output.getOut()).contains("taking source with name : 'one'");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'one-dev' because "
					+ "it ends in : 'dev' and we assume that is a profile name (and there are no active profiles)");
	}

	/**
	 * <pre>
	 *     - profiles are [a, b]
	 *     - first source present : 'one-a'
	 *     - second source present: 'two-b'
	 *
	 *     - both sources are taken
	 * </pre>
	 */
	@Test
	void testBothSourcesMatchProfiles(CapturedOutput output) {
		List<StrippedSourceContainer> sources = List.of(new StrippedSourceContainer(Map.of(), "one-a", Map.of()),
				new StrippedSourceContainer(Map.of(), "two-b", Map.of()));
		Set<String> profiles = Set.of("a", "b").stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.get(0).name()).isEqualTo("one-a");
		Assertions.assertThat(result.get(1).name()).isEqualTo("two-b");
		Assertions.assertThat(output.getOut())
			.contains("taking source with name : 'one-a' because it ends in : "
					+ "'a' and that is an active profile in profiles : [a, b]");
		Assertions.assertThat(output.getOut())
			.contains("taking source with name : 'two-b' because it ends in : "
					+ "'b' and that is an active profile in profiles : [a, b]");
	}

	/**
	 * <pre>
	 *     - profiles are [a, b]
	 *     - first source present : 'one-a'
	 *     - second source present: 'two-c'
	 *     - third source present: 'three-no-match'
	 *
	 *     - only first source is taken
	 * </pre>
	 */
	@Test
	void testTwoSourcesOneMatchesProfiles(CapturedOutput output) {
		List<StrippedSourceContainer> sources = List.of(new StrippedSourceContainer(Map.of(), "one-a", Map.of()),
				new StrippedSourceContainer(Map.of(), "two-c", Map.of()),
				new StrippedSourceContainer(Map.of(), "three-no-match", Map.of()));
		Set<String> profiles = Set.of("a", "b").stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).name()).isEqualTo("one-a");
		Assertions.assertThat(output.getOut())
			.contains("taking source with name : 'one-a' because it ends in : "
					+ "'a' and that is an active profile in profiles : [a, b]");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'two-c' because it ends in : "
					+ "'c' and it does not match any of the active profiles : [a, b]");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'three-no-match' because "
					+ "it ends in : 'no-match' and it does not match any of the active profiles : [a, b]");
	}

	/**
	 * <pre>
	 *     - profiles are [a, b]
	 *     - first source present : 'one-a'
	 *     - second source present: 'two-c'
	 *     - third source present: 'three-no-match'
	 *     - fourth source present: 'taken'
	 *
	 *     - only first source is taken
	 * </pre>
	 */
	@Test
	void testThreeSourcesTwoMatchProfiles(CapturedOutput output) {
		List<StrippedSourceContainer> sources = List.of(new StrippedSourceContainer(Map.of(), "one-a", Map.of()),
				new StrippedSourceContainer(Map.of(), "two-c", Map.of()),
				new StrippedSourceContainer(Map.of(), "three-no-match", Map.of()),
				new StrippedSourceContainer(Map.of(), "taken", Map.of()));
		Set<String> profiles = Set.of("a", "b").stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
		List<StrippedSourceContainer> result = profilesFiltered(sources, profiles);
		Assertions.assertThat(result.size()).isEqualTo(2);

		// the order matters here, non-profile based sources come first
		Assertions.assertThat(result.get(0).name()).isEqualTo("taken");
		Assertions.assertThat(result.get(1).name()).isEqualTo("one-a");
		Assertions.assertThat(output.getOut())
			.contains("taking source with name : 'one-a' because it ends in : "
					+ "'a' and that is an active profile in profiles : [a, b]");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'two-c' because it ends in : "
					+ "'c' and it does not match any of the active profiles : [a, b]");
		Assertions.assertThat(output.getOut())
			.contains("skipping source with name : 'three-no-match' because "
					+ "it ends in : 'no-match' and it does not match any of the active profiles : [a, b]");
		Assertions.assertThat(output.getOut()).contains("taking source with name : 'taken'");
	}

}
