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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.Prefix;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.sourceDataName;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.sourceName;
import static org.springframework.cloud.kubernetes.commons.config.Constants.ERROR_PROPERTY;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;
import static org.springframework.cloud.kubernetes.commons.config.SourceDataFlattener.defaultFlattenedSourceData;
import static org.springframework.cloud.kubernetes.commons.config.SourceDataFlattener.nameFlattenedSourceData;
import static org.springframework.cloud.kubernetes.commons.config.SourceDataFlattener.prefixFlattenedSourceData;

/**
 * @author wind57
 *
 * Base class when dealing with labeled sources that support profiles specific sources,
 * prefix based properties and single file yaml/properties.
 */
public abstract class LabeledSourceData {

	private static final Log LOG = LogFactory.getLog(LabeledSourceData.class);

	public final SourceData compute(Map<String, String> labels, Prefix prefix, String target, boolean profileSources,
			boolean failFast, String namespace, String[] activeProfiles) {

		MultipleSourcesContainer data = MultipleSourcesContainer.empty();
		String sourceDataName;

		try {
			Set<String> profiles = Set.of();
			if (profileSources) {
				profiles = Arrays.stream(activeProfiles).collect(Collectors.toSet());
			}
			data = dataSupplier(labels, profiles);
			LinkedHashMap<String, Map<String, Object>> sourceData = data.data();
			sourceDataName = sourceDataName(target, sourceData.keySet(), namespace);

			if (sourceData.isEmpty()) {
				return emptySourceData(labels, target, namespace);
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
			LOG.warn("Failure in reading labeled sources");
			onException(failFast, e);
			return new SourceData(sourceDataName(target, data.data().keySet(), namespace),
					Map.of(ERROR_PROPERTY, "true"));
		}

	}

	/*
	 * When there is no data, the name of the property source is made from provided
	 * labels, unlike when the data is present: when we use secret names.
	 */
	private SourceData emptySourceData(Map<String, String> labels, String target, String namespace) {
		String sourceName = labels.keySet()
			.stream()
			.sorted()
			.collect(Collectors.collectingAndThen(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR),
					sortedLabels -> sourceName(target, sortedLabels, namespace)));

		return SourceData.emptyRecord(sourceName);
	}

	/**
	 * Implementation specific (fabric8 or k8s-native) way to get the data from then given
	 * source names.
	 * @param labels the ones that have been configured
	 * @param profiles profiles to taken into account when gathering source data. Can be
	 * empty.
	 * @return a container that holds the names of the source that were found and their
	 * data
	 */
	public abstract MultipleSourcesContainer dataSupplier(Map<String, String> labels, Set<String> profiles);

}
