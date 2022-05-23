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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
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
			Map<String, String> labels = source.labels();
			String namespace = context.namespace();
			String sourceName = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, labels.keySet());

			try {

				LOG.info("Loading Secret with labels '" + labels + "' in namespace '" + namespace + "'");
				List<V1Secret> secrets = context.client().listNamespacedSecret(namespace, null, null, null, null,
						createLabelsSelector(labels), null, null, null, null, null).getItems();

				if (!secrets.isEmpty()) {
					sourceName = secrets.stream().map(V1Secret::getMetadata).map(V1ObjectMeta::getName)
							.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));

					secrets.forEach(s -> result.putAll(dataFromSecret(s, namespace)));
				}

			}
			catch (Exception e) {
				String message = "Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'";
				onException(source.failFast(), message, e);
			}

			String propertySourceName = ConfigUtils.sourceName(source.target(), sourceName, namespace);
			return new SourceData(propertySourceName, result);
		};
	}

	private static String createLabelsSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
	}

}
