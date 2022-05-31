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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * @author wind57
 *
 * Base class when dealing with labeled sources that support profiles specific sources,
 * prefix based properties and single file yaml/properties.
 */
public abstract class LabeledSourceData {

	public final SourceData compute(Map<String, String> labels, ConfigUtils.Prefix prefix, String target,
			boolean profileSources, boolean failFast, String namespace, String[] activeProfiles) {

		try {
			Map.Entry<Set<String>, Map<String, Object>> dataFromLabels = dataSupplier(labels);
			Set<String> profileSourceNames = new HashSet<>();
			if (profileSources) {
				for (String sourceByLabel : dataFromLabels.getKey()) {
					for (String activeProfile : activeProfiles) {
						profileSourceNames.add(sourceByLabel + "-" + activeProfile);
					}
				}
			}

			Map.Entry<Set<String>, Map<String, Object>> dataFromProfiles = dataSupplier(profileSourceNames);
			Set<String> propertySourceNames = Stream
					.concat(dataFromLabels.getKey().stream(), dataFromProfiles.getKey().stream())
					.collect(Collectors.toSet());
			Map<String, Object> data = new HashMap<>();
			data.putAll(dataFromLabels.getValue());
			data.putAll(dataFromProfiles.getValue());

			if (prefix != ConfigUtils.Prefix.DEFAULT) {

				String prefixToUse;
				if (prefix == ConfigUtils.Prefix.KNOWN) {
					prefixToUse = prefix.prefixProvider().get();
				}
				else {
					prefixToUse = Stream.concat(dataFromLabels.getKey().stream(), dataFromProfiles.getKey().stream())
							.sorted().collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
				}

				PrefixContext prefixContext = new PrefixContext(data, prefixToUse, namespace, propertySourceNames);
				return ConfigUtils.withPrefix(target, prefixContext);
			}
		}
		catch (Exception e) {
			onException(failFast, e);
		}

		String names = labels.keySet().stream().sorted().collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
		return SourceData.emptyRecord(ConfigUtils.sourceName(target, names, namespace));
	}

	/**
	 * Implementation specific (fabric8 or k8s-native) way to get the data from then given
	 * source names.
	 * @param labels the ones that have been configured
	 * @return an Entry that holds the names of the source that were found and their data
	 */
	public abstract Map.Entry<Set<String>, Map<String, Object>> dataSupplier(Map<String, String> labels);

	/**
	 * Implementation specific (fabric8 or k8s-native) way to get the data from then given
	 * source names.
	 * @param sourceNames the ones that have been computed based on active profiles
	 * @return an Entry that holds the names of the source that were found and their data
	 */
	public abstract Map.Entry<Set<String>, Map<String, Object>> dataSupplier(Set<String> sourceNames);

}
