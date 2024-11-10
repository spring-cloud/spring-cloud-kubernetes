/*
 * Copyright 2013-2022 the original author or authors.
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;
import static org.springframework.cloud.kubernetes.commons.config.SourceData.EMPTY_SOURCE_NAME_ON_ERROR;

/**
 * @author wind57
 *
 * Base class when dealing with labeled sources that support profiles specific sources,
 * prefix based properties and single file yaml/properties.
 */
public abstract class LabeledSourceData {

	private static final Log LOG = LogFactory.getLog(LabeledSourceData.class);

	public final SourceData compute(Map<String, String> labels, ConfigUtils.Prefix prefix, String target,
			boolean profileSources, boolean failFast, String namespace, String[] activeProfiles) {

		MultipleSourcesContainer data;

		try {
			Set<String> profiles = Set.of();
			if (profileSources) {
				profiles = Arrays.stream(activeProfiles).collect(Collectors.toSet());
			}
			data = dataSupplier(labels, profiles);

			// need this check because when there is no data, the name of the property
			// source is using provided labels,
			// unlike when the data is present: when we use secret names
			if (data.names().isEmpty()) {
				String names = labels.keySet()
					.stream()
					.sorted()
					.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
				return SourceData.emptyRecord(ConfigUtils.sourceName(target, names, namespace));
			}

			if (prefix != ConfigUtils.Prefix.DEFAULT) {

				String prefixToUse;
				if (prefix == ConfigUtils.Prefix.KNOWN) {
					prefixToUse = prefix.prefixProvider().get();
				}
				else {
					prefixToUse = data.names()
						.stream()
						.sorted()
						.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
				}

				PrefixContext prefixContext = new PrefixContext(data.data(), prefixToUse, namespace, data.names());
				return ConfigUtils.withPrefix(target, prefixContext);
			}
		}
		catch (Exception e) {
			LOG.warn("failure in reading labeled sources");
			onException(failFast, e);
			return SourceData.emptyRecord(EMPTY_SOURCE_NAME_ON_ERROR);
		}

		String names = data.names().stream().sorted().collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
		return new SourceData(ConfigUtils.sourceName(target, names, namespace), data.data());

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
