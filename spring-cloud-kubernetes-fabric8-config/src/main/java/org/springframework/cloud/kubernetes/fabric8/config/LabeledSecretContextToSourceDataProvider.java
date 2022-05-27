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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.PrefixContext;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.secretDataByLabels;

/**
 * Provides an implementation of {@link Fabric8ContextToSourceData} for a labeled secret.
 *
 * @author wind57
 */
final class LabeledSecretContextToSourceDataProvider implements Supplier<Fabric8ContextToSourceData> {

	LabeledSecretContextToSourceDataProvider() {
	}

	/*
	 * Computes a ContextSourceData (think content) for secret(s) based on some labels.
	 * There could be many secrets that are read based on incoming labels, for which we
	 * will be computing a single Map<String, Object> in the end.
	 *
	 * If there is no secret found for the provided labels, we will return an "empty"
	 * SourceData. Its name is going to be the concatenated labels mapped to an empty Map.
	 *
	 * If we find secret(s) for the provided labels, its name is going to be the
	 * concatenated secret names mapped to the data they hold as a Map.
	 */
	@Override
	public Fabric8ContextToSourceData get() {

		return context -> {

			LabeledSecretNormalizedSource source = ((LabeledSecretNormalizedSource) context.normalizedSource());
			Set<String> propertySourceNames = new LinkedHashSet<>();
			Map<String, String> labels = source.labels();

			Map<String, Object> result = new HashMap<>();
			String namespace = context.namespace();
			String sourceNameFromLabels = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, labels.keySet());

			try {

				Map.Entry<String, Map<String, Object>> entry = secretDataByLabels(context.client(), namespace, labels);
				if (!entry.getValue().isEmpty()) {

					propertySourceNames.add(entry.getKey());
					result.putAll(entry.getValue());

					// we found the source, it has prefix configured
					if (source.prefix() != ConfigUtils.Prefix.DEFAULT) {

						String prefix;
						if (source.prefix() == ConfigUtils.Prefix.KNOWN) {
							prefix = source.prefix().prefixProvider().get();
						}
						else {
							prefix = entry.getKey();
						}

						PrefixContext prefixContext = new PrefixContext(result, prefix, namespace, propertySourceNames);
						return ConfigUtils.withPrefix(source.target(), prefixContext);
					}

					// we found the source, it has no prefix configured
					String names = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, propertySourceNames);
					return new SourceData(ConfigUtils.sourceName(source.target(), names, namespace), result);
				}

			}
			catch (Exception e) {
				String message = "Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'";
				onException(source.failFast(), message, e);
			}

			// if we could not find the source with provided labels. Will compute a
			// response with an empty Map
			// and name that will use all the label names (not their values)
			String propertySourceNameFromLabels = ConfigUtils.sourceName(source.target(), sourceNameFromLabels,
					namespace);
			return new SourceData(propertySourceNameFromLabels, result);
		};
	}

}
