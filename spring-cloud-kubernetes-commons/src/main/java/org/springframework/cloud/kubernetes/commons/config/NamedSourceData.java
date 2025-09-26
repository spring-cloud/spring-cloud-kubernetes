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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.Prefix;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.sourceName;
import static org.springframework.cloud.kubernetes.commons.config.Constants.ERROR_PROPERTY;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;
import static org.springframework.cloud.kubernetes.commons.config.SourceDataFlattener.defaultFlattenedSourceData;
import static org.springframework.cloud.kubernetes.commons.config.SourceDataFlattener.nameFlattenedSourceData;
import static org.springframework.cloud.kubernetes.commons.config.SourceDataFlattener.prefixFlattenedSourceData;

/**
 * @author wind57
 *
 * Base class when dealing with named sources that support profiles specific sources,
 * prefix based properties and single file yaml/properties.
 */
public abstract class NamedSourceData {

	private static final Log LOG = LogFactory.getLog(NamedSourceData.class);

	public final SourceData compute(String sourceName, Prefix prefix, String target, boolean profileSources,
			boolean failFast, String namespace, String[] activeProfiles) {

		// first comes a non-profile-based source
		LinkedHashSet<String> sourceNamesToSearchFor = new LinkedHashSet<>();
		sourceNamesToSearchFor.add(sourceName);

		MultipleSourcesContainer data = MultipleSourcesContainer.empty();
		String sourceDataName;

		try {
			if (profileSources) {
				for (String activeProfile : activeProfiles) {
					sourceNamesToSearchFor.add(sourceName + "-" + activeProfile);
				}
			}

			data = dataSupplier(sourceNamesToSearchFor);
			LinkedHashMap<String, Map<String, Object>> sourceData = data.data();

			Set<String> sourceNamesFound = sourceData.keySet();
			String sortedNames = sourceNamesFound.stream()
				.sorted()
				.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
			sourceDataName = generateSourceName(target, sortedNames, namespace, activeProfiles);

			if (sourceData.isEmpty()) {
				return emptySourceData(target, sourceName, namespace);
			}

			if (prefix.getName().equals(Prefix.DEFAULT.getName())) {
				return new SourceData(sourceDataName, defaultFlattenedSourceData(sourceData));
			}

			if (prefix.getName().equals(Prefix.KNOWN.getName())) {
				return new SourceData(sourceDataName,
						prefixFlattenedSourceData(sourceData, prefix.prefixProvider().get()));
			}

			if (prefix.getName().equals(Prefix.DELAYED.getName())) {
				return new SourceData(sourceDataName, nameFlattenedSourceData(sourceData));
			}

			throw new IllegalArgumentException("Unsupported prefix: " + prefix);

		}
		catch (Exception e) {
			LOG.warn("Failure in reading named sources");
			onException(failFast, e);
			String names = data.data()
				.keySet()
				.stream()
				.sorted()
				.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
			return new SourceData(generateSourceName(target, names, namespace, activeProfiles),
					Map.of(ERROR_PROPERTY, "true"));
		}

	}

	private SourceData emptySourceData(String target, String sourceName, String namespace) {
		String emptySourceName = sourceName(target, sourceName, namespace);
		LOG.debug("Will return empty source with name : " + emptySourceName);
		return SourceData.emptyRecord(emptySourceName);
	}

	protected String generateSourceName(String target, String sourceName, String namespace, String[] activeProfiles) {
		return ConfigUtils.sourceName(target, sourceName, namespace);
	}

	/**
	 * Implementation specific (fabric8 or k8s-native) way to get the data from then given
	 * source names.
	 * @param sourceNames the ones that have been configured, LinkedHashSet in order ot
	 * preserve the order: non-profile source first and then the rest
	 * @return an Entry that holds the names of the source that were found and their data
	 */
	public abstract MultipleSourcesContainer dataSupplier(LinkedHashSet<String> sourceNames);

}
