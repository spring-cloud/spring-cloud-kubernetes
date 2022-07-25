/*
 * Copyright 2013-2019 the original author or authors.
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.config.Constants.FALLBACK_APPLICATION_NAME;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;
import static org.springframework.cloud.kubernetes.commons.config.Constants.SPRING_APPLICATION_NAME;

/**
 * Utility class that works with configuration properties.
 *
 * @author Ioannis Canellos
 */
public final class ConfigUtils {

	private static final Log LOG = LogFactory.getLog(ConfigUtils.class);

	private ConfigUtils() {
	}

	public static String getApplicationName(Environment env, String configName, String configurationTarget) {
		if (!StringUtils.hasLength(configName)) {
			LOG.debug(configurationTarget + " name has not been set, taking it from property/env "
					+ SPRING_APPLICATION_NAME + " (default=" + FALLBACK_APPLICATION_NAME + ")");
			configName = env.getProperty(SPRING_APPLICATION_NAME, FALLBACK_APPLICATION_NAME);
		}

		return configName;
	}

	/**
	 * @param explicitPrefix value of
	 * 'spring.cloud.kubernetes.config|secrets.sources.explicitPrefix'
	 * @param useNameAsPrefix value of
	 * 'spring.cloud.kubernetes.config|secrets.sources.useNameAsPrefix'
	 * @param defaultUseNameAsPrefix value of
	 * 'spring.cloud.kubernetes.config|secrets.defaultUseNameAsPrefix'
	 * @param normalizedName either the name of
	 * 'spring.cloud.kubernetes.config|secrets.sources.name' or
	 * 'spring.cloud.kubernetes.config|secrets.name'
	 * @return prefix to use in normalized sources
	 */
	public static Prefix findPrefix(String explicitPrefix, Boolean useNameAsPrefix, boolean defaultUseNameAsPrefix,
			String normalizedName) {
		// if explicitPrefix is set, it takes priority over useNameAsPrefix
		// (either the one from 'spring.cloud.kubernetes.config|secrets' or
		// 'spring.cloud.kubernetes.config|secrets.sources')
		if (StringUtils.hasText(explicitPrefix)) {
			Prefix.computeKnown(() -> explicitPrefix);
			return Prefix.KNOWN;
		}

		// useNameAsPrefix is a java.lang.Boolean and if it's != null, users have
		// specified it explicitly
		if (useNameAsPrefix != null) {
			if (useNameAsPrefix) {
				// this is the case when the source is searched by labels,
				// but prefix support is enabled.
				// in such cases, the name is not known yet.
				if (normalizedName == null) {
					return Prefix.DELAYED;
				}

				Prefix.computeKnown(() -> normalizedName);
				return Prefix.KNOWN;
			}
			return Prefix.DEFAULT;
		}

		if (defaultUseNameAsPrefix) {

			// this is the case when the source is searched by labels,
			// but prefix support is enabled.ConfigUtilsTests
			// in such cases, the name is not known yet.
			if (normalizedName == null) {
				return Prefix.DELAYED;
			}

			Prefix.computeKnown(() -> normalizedName);
			return Prefix.KNOWN;
		}

		return Prefix.DEFAULT;
	}

	/**
	 * @param defaultIncludeProfileSpecificSources value of
	 * 'spring.cloud.kubernetes.config.includeProfileSpecificSources'
	 * @param includeProfileSpecificSources value of
	 * 'spring.cloud.kubernetes.config.sources.includeProfileSpecificSources'
	 * @return useProfileNameAsPrefix to be used in normalized sources
	 */
	public static boolean includeProfileSpecificSources(boolean defaultIncludeProfileSpecificSources,
			Boolean includeProfileSpecificSources) {
		if (includeProfileSpecificSources != null) {
			return includeProfileSpecificSources;
		}
		return defaultIncludeProfileSpecificSources;
	}

	/**
	 * action to take when an Exception happens when dealing with a source.
	 */
	public static void onException(boolean failFast, Exception e) {
		if (failFast) {
			if (e instanceof StrictSourceNotFoundException) {
				throw (StrictSourceNotFoundException) e;
			}
			throw new IllegalStateException(e.getMessage(), e);
		}

		if (e instanceof StrictSourceNotFoundException) {
			throw (StrictSourceNotFoundException) e;
		}

		LOG.warn(e.getMessage() + ". Ignoring.", e);
	}

	/*
	 * this method will return a SourceData that has a name in the form :
	 * "configmap.my-configmap.my-configmap-2.namespace" and the "data" from the context
	 * is appended with prefix. So if incoming is "a=b", the result will be : "prefix.a=b"
	 */
	public static SourceData withPrefix(String target, PrefixContext context) {
		Map<String, Object> withPrefix = CollectionUtils.newHashMap(context.data().size());
		context.data().forEach((key, value) -> withPrefix.put(context.prefix() + "." + key, value));

		String propertySourceTokens = String.join(PROPERTY_SOURCE_NAME_SEPARATOR,
				context.propertySourceNames().stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new)));
		return new SourceData(sourceName(target, propertySourceTokens, context.namespace()), withPrefix);
	}

	public static String sourceName(String target, String applicationName, String namespace) {
		return target + PROPERTY_SOURCE_NAME_SEPARATOR + applicationName + PROPERTY_SOURCE_NAME_SEPARATOR + namespace;
	}

	/**
	 * transforms raw data from one or multiple sources into an entry of source names and
	 * flattened data that they all hold (potentially overriding entries without any
	 * defined order).
	 */
	public static MultipleSourcesContainer processNamedData(List<StrippedSourceContainer> strippedSources,
			Environment environment, LinkedHashSet<StrictSource> sources, String namespace, boolean decode) {

		Map<String, StrippedSourceContainer> hashByName = hashByName(strippedSources);
		LinkedHashSet<String> foundSourceNames = new LinkedHashSet<>();
		Map<String, Object> data = new HashMap<>();

		// this is an ordered stream, and it means that non-profile based sources will be
		// processed before profile based sources. This way, we replicate that
		// "application-dev.yaml" overrides properties from "application.yaml"
		sources.forEach(source -> {
			String name = source.name();
			StrippedSourceContainer stripped = hashByName.get(source.name());

			if (stripped == null && source.strict()) {
				LOG.error("source with name : " + name + " must be present in namespace: : " + namespace);
				throw new StrictSourceNotFoundException("source : " + name + " not present in namespace: " + namespace);
			}

			if (stripped == null) {
				LOG.warn("source with name : " + name + " not found in namespace : " + namespace + ". Ignoring.");
			}

			if (stripped != null) {
				LOG.debug("Found source : '" + source + " in namespace: '" + namespace + "'");
				foundSourceNames.add(name);
				// see if data is a single yaml/properties file and if it needs decoding
				Map<String, String> rawData = stripped.data();
				if (decode) {
					rawData = decodeData(rawData);
				}
				data.putAll(SourceDataEntriesProcessor.processAllEntries(rawData == null ? Map.of() : rawData,
						environment));
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
	public static MultipleSourcesContainer processLabeledData(List<StrippedSourceContainer> strippedSources,
			Environment environment, Map<String, String> labels, String namespace, Set<StrictProfile> profiles,
			boolean decode, boolean strict) {

		// find sources by provided labels. strippedSources contains all the sources in
		// the namespace,
		// we narrow those all to the ones that match our labels only.
		List<StrippedSourceContainer> byLabels = strippedSources.stream().filter(one -> {
			Map<String, String> sourceLabels = one.labels();
			Map<String, String> labelsToSearchAgainst = sourceLabels == null ? Map.of() : sourceLabels;
			return labelsToSearchAgainst.entrySet().containsAll((labels.entrySet()));
		}).collect(Collectors.toList());

		if (byLabels.isEmpty() && strict) {
			LOG.error("source(s) with labels : " + labels + " must be present in namespace : " + namespace);
			throw new StrictSourceNotFoundException(
					"source(s) with labels : " + labels + " not present in namespace: " + namespace);
		}

		// once we know the names of configmaps (that we found by labels above),
		// we can find out their profile based siblings. This just computes their names,
		// nothing more.
		List<StrictSource> sourceNamesByLabelsWithProfile = new ArrayList<>();
		if (profiles != null && !profiles.isEmpty()) {
			for (StrippedSourceContainer one : byLabels) {
				for (StrictProfile profile : profiles) {
					String name = one.name() + "-" + profile.name();
					sourceNamesByLabelsWithProfile.add(new StrictSource(name, profile.strict()));
				}
			}
		}

		Map<String, StrippedSourceContainer> hashByName = hashByName(strippedSources);

		// once we know sources by labels (and thus their names), we can find out
		// profiles based sources from the above. This would get all sources
		// we are interested in.
		List<StrippedSourceContainer> byProfile = sourceNamesByLabelsWithProfile.stream().map(one -> {
			StrippedSourceContainer strippedSource = hashByName.get(one.name());
			if (strippedSource == null && one.strict()) {
				LOG.error("source : " + one.name() + " must be present in namespace: " + namespace);
				throw new StrictSourceNotFoundException(
						"source : " + one.name() + " not present in namespace: " + namespace);
			}

			if (strippedSource == null) {
				LOG.warn("source with name : " + one.name() + " not found in namespace : " + namespace + ". Ignoring.");
			}

			return strippedSource;
		}).filter(Objects::nonNull).collect(Collectors.toList());

		// this makes sure that we first have "app" and then "app-dev" in the list
		List<StrippedSourceContainer> all = new ArrayList<>(byLabels.size() + byProfile.size());
		all.addAll(byLabels);
		all.addAll(byProfile);

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

	public static boolean noSources(List<?> sources, String namespace) {
		if (sources == null || sources.isEmpty()) {
			LOG.debug("No sources in namespace '" + namespace + "'");
			return true;
		}
		return false;
	}

	public static LinkedHashSet<StrictProfile> profiles(boolean includeProfileSpecificSources,
			LinkedHashSet<String> strictForProfiles, Environment environment) {

		String[] activeProfiles = environment.getActiveProfiles();
		Set<String> activeProfilesAsSet = Arrays.stream(activeProfiles).collect(Collectors.toSet());

		if (!includeProfileSpecificSources) {
			LOG.debug("include-profile-specific-sources is false, thus ignoring strict-for-profiles");
			return new LinkedHashSet<>();
		}

		if (strictForProfiles.isEmpty()) {
			LOG.debug("include-profile-specific-sources = true, strict-for-profiles = empty");
			return StrictProfile.allNonStrict(activeProfiles);
		}

		// include-profile-specific-sources = true, strict-for-profiles != empty
		// 1. make sure that strict-for-profiles are all present
		for (String profile : strictForProfiles) {
			boolean isPresent = activeProfilesAsSet.contains(profile);
			if (!isPresent) {
				throw new StrictSourceNotFoundException("profile : " + profile + " is not an active profile, but there "
						+ "is source definition for it under 'strict-for-profiles'");
			}
		}

		// 2. non-strict profiles are supposed to come first, since we treat them as
		// "optional"
		LinkedHashSet<StrictProfile> result = new LinkedHashSet<>();
		for (String profile : activeProfiles) {
			if (!strictForProfiles.contains(profile)) {
				result.add(new StrictProfile(profile, false));
			}
		}

		// 3. strict-for-profiles will come in strict order
		result.addAll(StrictProfile.allStrict(strictForProfiles));
		return result;

	}

	private static Map<String, String> decodeData(Map<String, String> data) {
		Map<String, String> result = new HashMap<>(CollectionUtils.newHashMap(data.size()));
		data.forEach((key, value) -> result.put(key, new String(Base64.getDecoder().decode(value)).trim()));
		return result;
	}

	private static Map<String, StrippedSourceContainer> hashByName(List<StrippedSourceContainer> strippedSources) {
		return strippedSources.stream().collect(Collectors.toMap(StrippedSourceContainer::name, Function.identity()));
	}

	public static final class Prefix {

		/**
		 * prefix has not been provided.
		 */
		public static final Prefix DEFAULT = new Prefix(() -> "", "DEFAULT");

		/**
		 * prefix has been enabled, but the actual value will be known later; the value
		 * for the prefix will be the name of the source. (this is the case for a
		 * prefix-enabled labeled source for example)
		 */
		public static final Prefix DELAYED = new Prefix(() -> {
			throw new IllegalArgumentException("prefix is delayed, needs to be taken elsewhere");
		}, "DELAYED");

		/**
		 * prefix is known at the callsite.
		 */
		public static Prefix KNOWN;

		public Supplier<String> prefixProvider() {
			return prefixProvider;
		}

		private final Supplier<String> prefixProvider;

		private final String name;

		private Prefix(Supplier<String> prefixProvider, String name) {
			this.prefixProvider = prefixProvider;
			this.name = name;
		}

		private static void computeKnown(Supplier<String> supplier) {
			KNOWN = new Prefix(supplier, "KNOWN");
		}

		public String toString() {
			return new ToStringCreator(this).append("name", name).toString();
		}

	}

}
