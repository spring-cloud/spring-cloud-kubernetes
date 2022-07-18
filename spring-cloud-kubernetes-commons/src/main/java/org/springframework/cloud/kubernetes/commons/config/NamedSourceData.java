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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * @author wind57
 *
 * Base class when dealing with named sources that support profiles specific sources,
 * prefix based properties and single file yaml/properties.
 */
public abstract class NamedSourceData {

	public final SourceData compute(String sourceName, ConfigUtils.Prefix prefix, String target, boolean profileSources,
			boolean failFast, String namespace, String[] activeProfiles) {

		LinkedHashSet<String> sourceNames = new LinkedHashSet<>();
		// first comes non-profile based source
		sourceNames.add(sourceName);

		MultipleSourcesContainer data = MultipleSourcesContainer.empty();

		try {
			if (profileSources) {
				for (String activeProfile : activeProfiles) {
					// add all profile based sources _after_ non-profile based one
					sourceNames.add(sourceName + "-" + activeProfile);
				}
			}

			data = dataSupplier(sourceNames);

			if (data.names().isEmpty()) {
				return new SourceData(ConfigUtils.sourceName(target, sourceName, namespace), Map.of());
			}

			if (prefix != ConfigUtils.Prefix.DEFAULT) {
				// since we are in a named source, calling get on the supplier is safe
				String prefixToUse = prefix.prefixProvider().get();
				PrefixContext prefixContext = new PrefixContext(data.data(), prefixToUse, namespace, data.names());
				return ConfigUtils.withPrefix(target, prefixContext);
			}

		}
		catch (Exception e) {
			onException(failFast, e);
		}

		String names = data.names().stream().sorted().collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
		return new SourceData(ConfigUtils.sourceName(target, names, namespace), data.data());
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
