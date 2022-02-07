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
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;

import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * Provides an implementation of {@link KubernetesClientContextToSourceData} for a labeled secret.
 *
 * @author wind57
 */
final class LabeledSecretContextToSourceDataProvider implements Supplier<KubernetesClientContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(NamedConfigMapContextToSourceDataProvider.class);

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
			Map<String, String> labels = ((LabeledSecretNormalizedSource) context.normalizedSource()).getLabels();
			String namespace = context.namespace();
			// name is either the concatenated labels or the concatenated names
			// of the secrets that match these labels
			String name = labels.entrySet().stream().map(en -> en.getKey() + ":" + en.getValue())
					.collect(Collectors.joining("#"));

			try {

				LOG.info("Loading Secret with labels '" + labels + "'in namespace '" + namespace + "'");
				List<V1Secret> secrets = context.client().listNamespacedSecret(namespace, null, null, null, null,
						createLabelsSelector(labels), null, null, null, null, null).getItems();

				name = secrets.stream().map(V1Secret::getMetadata).map(V1ObjectMeta::getName)
						.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));

				secrets.forEach(s -> putAll(s, result));

			}
			catch (Exception e) {
				if (context.normalizedSource()) {
					throw new IllegalStateException(
							"Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read secret with labels [" + labels + "] in namespace: '" + namespace + "' (cause: "
						+ e.getMessage() + "). Ignoring");
			}

			String sourceName = getSourceName(name, namespace);
			return new AbstractMap.SimpleImmutableEntry<>(sourceName, result);
		};
	}

	private static String createLabelsSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
	}

//	private static void putAll(V1Secret secret, Map<String, Object> result) {
//		Map<String, String> secretData = new HashMap<>();
//		if (secret.getData() != null) {
//			secret.getData().forEach((key, value) -> secretData.put(key, Base64.getEncoder().encodeToString(value)));
//			putAll(secretData, result);
//		}
//	}
}
