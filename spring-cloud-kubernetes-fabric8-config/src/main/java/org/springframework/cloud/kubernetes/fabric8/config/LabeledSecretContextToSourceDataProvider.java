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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.dataFromSecret;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * Provides an implementation of {@link ContextToSourceData} for a labeled secret.
 *
 * @author wind57
 */
final class LabeledSecretContextToSourceDataProvider implements Supplier<ContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(LabeledSecretContextToSourceDataProvider.class);

	private final BiFunction<String, String, String> sourceNameFunction;

	private LabeledSecretContextToSourceDataProvider(BiFunction<String, String, String> sourceNameFunction) {
		this.sourceNameFunction = Objects.requireNonNull(sourceNameFunction);
	}

	static LabeledSecretContextToSourceDataProvider of(BiFunction<String, String, String> sourceNameFunction) {
		return new LabeledSecretContextToSourceDataProvider(sourceNameFunction);
	}

	/*
	 * Computes a ContextSourceData (think content) for secret(s) based on some labels.
	 * There could be many secrets that are read based on incoming labels, for which
	 * we will be computing a single Map<String, Object> in the end.
	 *
	 * If there is no secret found for the provided labels, we will return an "empty" SourceData.
	 * Its name is going to be the concatenated labels mapped to an empty Map.
	 *
	 * If we find secret(s) for the provided labels, its name is going to be the concatenated secret names
	 * mapped to the data they hold as a Map.
	 */
	@Override
	public ContextToSourceData get() {

		return fabric8ConfigContext -> {

			LabeledSecretNormalizedSource source = ((LabeledSecretNormalizedSource) fabric8ConfigContext.normalizedSource());
			Map<String, String> labels = source.getLabels();

			Map<String, Object> result = new HashMap<>();
			String namespace = fabric8ConfigContext.namespace();
			String name = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, labels.keySet());

			try {

				LOG.info("Loading Secret(s) with labels '" + labels + "' in namespace '" + namespace + "'");
				List<Secret> secrets = fabric8ConfigContext.client().secrets().inNamespace(namespace).withLabels(labels).list()
					.getItems();

				if (!secrets.isEmpty()) {
					secrets.forEach(secret -> result.putAll(dataFromSecret(secret, namespace)));
					name = secrets.stream().map(Secret::getMetadata).map(ObjectMeta::getName)
						.collect(Collectors.joining(PROPERTY_SOURCE_NAME_SEPARATOR));
				} else {
					LOG.info("No Secret(s) with labels '" + labels + "' in namespace '" + namespace + "' found.");
				}

			}
			catch (Exception e) {
				if (source.isFailFast()) {
					throw new IllegalStateException(
						"Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read secret with labels [" + labels + "] in namespace: '" + namespace + "' (cause: "
					+ e.getMessage() + "). Ignoring");
			}

			String sourceName = sourceNameFunction.apply(name, namespace);
			return new SourceData(sourceName, result);
		};
	}

}
