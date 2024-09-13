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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;

final class ConfigUtilsDataProcessor {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(ConfigUtilsDataProcessor.class));

	private static final Pattern ENDS_WITH_PROFILE = Pattern.compile("^.+?-(.+$)");

	// sourceName (configmap or secret name) ends with : "-dev.yaml" or the like.
	private static final BiPredicate<String, String> ENDS_WITH_PROFILE_AND_EXTENSION = (sourceName,
			activeProfile) -> sourceName.endsWith("-" + activeProfile + ".yml")
					|| sourceName.endsWith("-" + activeProfile + ".yaml")
					|| sourceName.endsWith("-" + activeProfile + ".properties");

	private ConfigUtilsDataProcessor() {

	}

	/**
	 * transforms raw data from one or multiple sources into an entry of source names and
	 * flattened data that they all hold (potentially overriding entries without any
	 * defined order).
	 */
	static MultipleSourcesContainer processNamedData(List<StrippedSourceContainer> strippedSources,
			Environment environment, LinkedHashSet<String> sourceNames, String namespace, boolean decode,
			boolean includeDefaultProfileData) {

		Map<String, StrippedSourceContainer> hashByName = strippedSources.stream()
			.collect(Collectors.toMap(StrippedSourceContainer::name, Function.identity()));

		LinkedHashSet<String> foundSourceNames = new LinkedHashSet<>();
		Map<String, Object> data = new HashMap<>();

		// this is an ordered stream, and it means that non-profile based sources will be
		// processed before profile based sources. This way, we replicate that
		// "application-dev.yaml"
		// overrides properties from "application.yaml"
		sourceNames.forEach(sourceName -> {
			StrippedSourceContainer stripped = hashByName.get(sourceName);
			if (stripped != null) {
				LOG.debug("Found source with name : '" + sourceName + " in namespace: '" + namespace + "'");
				foundSourceNames.add(sourceName);
				// see if data is a single yaml/properties file and if it needs decoding
				Map<String, String> rawData = stripped.data();
				if (decode) {
					rawData = decodeData(rawData);
				}

				/*
				 * In some cases we want to include properties from the default profile
				 * along with any active profiles In these cases includeDefaultProfileData
				 * will be true If includeDefaultProfileData is false then we want to make
				 * sure that we only return properties from any active profiles
				 */
				if (processSource(includeDefaultProfileData, environment, sourceName, rawData)) {
					data.putAll(SourceDataEntriesProcessor.processAllEntries(rawData == null ? Map.of() : rawData,
							environment, includeDefaultProfileData));
				}
			}
		});

		return new MultipleSourcesContainer(foundSourceNames, data);
	}

	/**
	 * transforms raw data from one or multiple sources into an entry of source names and
	 * flattened data that they all hold (potentially overriding entries without any
	 * defined order). This method first searches by labels, find the sources, then uses
	 * these names to find any profile based sources.
	 */

	public static MultipleSourcesContainer processLabeledData(List<StrippedSourceContainer> containers,
			Environment environment, Map<String, String> labels, String namespace, Set<String> profiles,
			boolean decode) {

		// find sources that match the provided labels
		List<StrippedSourceContainer> allByLabels = containers.stream().filter(one -> {
			Map<String, String> sourceLabels = one.labels();
			Map<String, String> labelsToSearchAgainst = sourceLabels == null ? Map.of() : sourceLabels;
			return labelsToSearchAgainst.entrySet().containsAll((labels.entrySet()));
		}).toList();

		List<StrippedSourceContainer> all = profilesFiltered(allByLabels, profiles);

		LinkedHashSet<String> sourceNames = new LinkedHashSet<>();
		Map<String, Object> result = new HashMap<>();

		all.forEach(source -> {
			String foundSourceName = source.name();
			LOG.debug("Loaded source with name : '" + foundSourceName + " in namespace: '" + namespace + "'");
			sourceNames.add(foundSourceName);

			Map<String, String> rawData = source.data();
			if (decode) {
				rawData = decodeData(rawData);
			}
			result.putAll(SourceDataEntriesProcessor.processAllEntries(rawData, environment));
		});

		return new MultipleSourcesContainer(sourceNames, result);

	}

	static List<StrippedSourceContainer> profilesFiltered(List<StrippedSourceContainer> allByLabels,
			Set<String> profiles) {
		List<StrippedSourceContainer> allByLabelsNonProfileBased = new ArrayList<>();
		List<StrippedSourceContainer> allByLabelsProfileBased = new ArrayList<>();

		if (profiles == null || profiles.isEmpty()) {
			for (StrippedSourceContainer oneByLabel : allByLabels) {
				String name = oneByLabel.name();
				Matcher matcher = ENDS_WITH_PROFILE.matcher(name);
				if (matcher.matches()) {
					LOG.debug(
							() -> "skipping source with name : '" + name + "' because it ends in : '" + matcher.group(1)
									+ "' and we assume that is a profile name (and there are no active profiles)");
				}
				else {
					LOG.debug(() -> "taking source with name : '" + name + "'");
					allByLabelsNonProfileBased.add(oneByLabel);
				}
			}
		}
		else {
			for (StrippedSourceContainer oneByLabel : allByLabels) {
				String name = oneByLabel.name();
				Matcher matcher = ENDS_WITH_PROFILE.matcher(name);
				if (matcher.matches()) {
					String profile = matcher.group(1);
					if (profiles.contains(profile)) {
						LOG.debug(() -> "taking source with name : '" + name + "' because it ends in : '"
								+ matcher.group(1) + "' and that is an active profile in profiles : " + profiles);
						allByLabelsProfileBased.add(oneByLabel);
					}
					else {
						LOG.debug(() -> "skipping source with name : '" + name + "' because it ends in : '"
								+ matcher.group(1) + "' and it does not match any of the active profiles : "
								+ profiles);
					}
				}
				else {
					LOG.debug(() -> "taking source with name : '" + name + "'");
					allByLabelsNonProfileBased.add(oneByLabel);
				}
			}
		}

		List<StrippedSourceContainer> all = new ArrayList<>(
				allByLabelsNonProfileBased.size() + allByLabelsProfileBased.size());
		all.addAll(allByLabelsNonProfileBased);
		all.addAll(allByLabelsProfileBased);

		return all;
	}

	static boolean processSource(boolean includeDefaultProfileData, Environment environment, String sourceName,
			Map<String, String> sourceRawData) {
		List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles()).toList();

		boolean emptyActiveProfiles = activeProfiles.isEmpty();

		boolean profileBasedSourceName = activeProfiles.stream()
			.anyMatch(activeProfile -> sourceName.endsWith("-" + activeProfile));

		boolean defaultProfilePresent = activeProfiles.contains("default");

		return includeDefaultProfileData || emptyActiveProfiles || profileBasedSourceName || defaultProfilePresent
				|| rawDataContainsProfileBasedSource(activeProfiles, sourceRawData).getAsBoolean();
	}

	/*
	 * this one is not inlined into 'processSource' because other filters, that come
	 * before it, might have already resolved to 'true', so no need to compute it at all.
	 *
	 * This method is supposed to answer the question if raw data that a certain source
	 * (configmap or secret) has entries that are themselves profile based
	 * yaml/yml/properties. For example: 'account-k8s.yaml' or the like.
	 */
	static BooleanSupplier rawDataContainsProfileBasedSource(List<String> activeProfiles,
			Map<String, String> sourceRawData) {
		return () -> Optional.ofNullable(sourceRawData)
			.orElse(Map.of())
			.keySet()
			.stream()
			.anyMatch(keyName -> activeProfiles.stream()
				.anyMatch(activeProfile -> ENDS_WITH_PROFILE_AND_EXTENSION.test(keyName, activeProfile)));
	}

	private static Map<String, String> decodeData(Map<String, String> data) {
		Map<String, String> result = new HashMap<>(CollectionUtils.newHashMap(data.size()));
		data.forEach((key, value) -> result.put(key, new String(Base64.getDecoder().decode(value)).trim()));
		return result;
	}

}
