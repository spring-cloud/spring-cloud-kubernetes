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
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
			throw new IllegalStateException(e.getMessage(), e);
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
			Environment environment, Set<String> sourceNames, String namespace, boolean decode) {

		Set<String> foundSourceNames = new HashSet<>();
		Map<String, Object> data = new HashMap<>();

		strippedSources.stream().filter(source -> sourceNames.contains(source.name())).collect(Collectors.toList())
				.forEach(foundSource -> {
					String sourceName = foundSource.name();
					LOG.debug("Found source with name : '" + sourceName + " in namespace: '" + namespace + "'");
					foundSourceNames.add(sourceName);
					// see if data is a single yaml/properties file and if it needs
					// decoding
					Map<String, String> rawData = foundSource.data();
					if (decode) {
						rawData = decodeData(rawData);
					}
					data.putAll(SourceDataEntriesProcessor.processAllEntries(rawData == null ? Map.of() : rawData,
							environment));
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

		// find sources by provided labels
		List<StrippedSourceContainer> sourcesByLabels = containers.stream().filter(one -> {
			Map<String, String> sourceLabels = one.labels();
			Map<String, String> labelsToSearchAgainst = sourceLabels == null ? Map.of() : sourceLabels;
			return labelsToSearchAgainst.entrySet().containsAll((labels.entrySet()));
		}).collect(Collectors.toList());

		// compute profile based sources (based on the ones we found by labels)
		List<String> sourceNamesByLabelsWithProfile = new ArrayList<>();
		if (profiles != null && !profiles.isEmpty()) {
			for (StrippedSourceContainer one : sourcesByLabels) {
				for (String profile : profiles) {
					String name = one.name() + "-" + profile;
					sourceNamesByLabelsWithProfile.add(name);
				}
			}
		}

		// once we know sources by labels (and thus their names), we can find out
		// profiles based sources from the above. This would get all sources
		// we are interested in.
		List<StrippedSourceContainer> sourcesToTake = containers.stream()
				.filter(one -> sourceNamesByLabelsWithProfile.contains(one.name()))
				.collect(Collectors.toCollection(ArrayList::new));
		sourcesToTake.addAll(sourcesByLabels);

		Set<String> sourceNames = new HashSet<>();
		Map<String, Object> result = new HashMap<>();

		sourcesToTake.forEach(source -> {
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

	private static Map<String, String> decodeData(Map<String, String> data) {
		Map<String, String> result = new HashMap<>(CollectionUtils.newHashMap(data.size()));
		data.forEach((key, value) -> result.put(key, new String(Base64.getDecoder().decode(value)).trim()));
		return result;
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
