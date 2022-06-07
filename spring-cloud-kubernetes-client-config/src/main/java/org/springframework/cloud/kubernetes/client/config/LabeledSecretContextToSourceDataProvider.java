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

package org.springframework.cloud.kubernetes.client.config;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.PrefixContext;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.dataFromSecret;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * Provides an implementation of {@link KubernetesClientContextToSourceData} for a labeled
 * secret.
 *
 * @author wind57
 */
final class LabeledSecretContextToSourceDataProvider implements Supplier<KubernetesClientContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(LabeledSecretContextToSourceDataProvider.class);

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
	public KubernetesClientContextToSourceData get() {
		return context -> {

			Map<String, Object> result = new HashMap<>();
			LabeledSecretNormalizedSource source = (LabeledSecretNormalizedSource) context.normalizedSource();
			Set<String> propertySourceNames = new LinkedHashSet<>();
			Map<String, String> labels = source.labels();
			String namespace = context.namespace();
			String sourceNameFromLabels = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, labels.keySet());

			try {

				LOG.info("Loading Secret with labels '" + labels + "' in namespace '" + namespace + "'");
				List<V1Secret> secrets = context.client().listNamespacedSecret(namespace, null, null, null, null,
						createLabelsSelector(labels), null, null, null, null, null).getItems();

				if (!secrets.isEmpty()) {

					for (V1Secret secret : secrets) {
						// we support prefix per source, not per secret. This means that
						// in theory
						// we can still have clashes here, that we simply override.
						// If there are more than one secret found per labels, and they
						// have the same key on a
						// property, but different values; one value will override the
						// other, without any particular order.
						result.putAll(dataFromSecret(secret, namespace));
					}

					String secretNames = secrets.stream().map(V1Secret::getMetadata).map(V1ObjectMeta::getName).sorted()
							.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
					propertySourceNames.add(secretNames);

					if (source.prefix() != ConfigUtils.Prefix.DEFAULT) {

						String prefix;
						if (source.prefix() == ConfigUtils.Prefix.KNOWN) {
							prefix = source.prefix().prefixProvider().get();
						}
						else {
							// prefix is going to be all the secret names we found based
							// on the labels
							// concatenated with PROPERTY_SOURCE_NAME_SEPARATOR
							prefix = secretNames;
						}

						PrefixContext prefixContext = new PrefixContext(result, prefix, namespace, propertySourceNames);
						return ConfigUtils.withPrefix(source.target(), prefixContext);
					}

					String names = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, propertySourceNames);
					return new SourceData(ConfigUtils.sourceName(source.target(), names, namespace), result);

				}

			}
			catch (Exception e) {
				String message = "Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'";
				onException(source.failFast(), message, e);
			}

			// if we could not find a secret with provided labels, we will compute a
			// response with an empty Map
			// and name that will use all the label names (not their values)
			String propertySourceNameFromLabels = ConfigUtils.sourceName(source.target(), sourceNameFromLabels,
					namespace);
			return new SourceData(propertySourceNameFromLabels, result);
		};
	}

	private static String createLabelsSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
	}

}
