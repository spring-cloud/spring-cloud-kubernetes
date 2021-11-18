/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.AbstractMap;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSourceType;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientSecretsPropertySource extends SecretsPropertySource {

	private static final Log LOG = LogFactory.getLog(KubernetesClientSecretsPropertySource.class);

	private static final EnumMap<NormalizedSourceType, Function<KubernetesClientConfigContext, Map.Entry<String, Map<String, Object>>>> STRATEGIES = new EnumMap<>(
			NormalizedSourceType.class);

	static {
		STRATEGIES.put(NormalizedSourceType.NAMED_SECRET, namedSecret());
		STRATEGIES.put(NormalizedSourceType.LABELED_SECRET, labeledSecret());
	}

	public KubernetesClientSecretsPropertySource(KubernetesClientConfigContext context) {
		super(getSourceData(context));

	}

	private static Map.Entry<String, Map<String, Object>> getSourceData(KubernetesClientConfigContext context) {
		NormalizedSourceType type = context.getNormalizedSource().type();
		return Optional.ofNullable(STRATEGIES.get(type)).map(x -> x.apply(context))
				.orElseThrow(() -> new IllegalArgumentException("no strategy found for : " + type));
	}

	private static Function<KubernetesClientConfigContext, Map.Entry<String, Map<String, Object>>> namedSecret() {
		return context -> {

			Map<String, Object> result = new HashMap<>();
			String name = ((NamedSecretNormalizedSource) context.getNormalizedSource()).getName();
			String namespace = context.getAppNamespace();

			try {

				LOG.info("Loading Secret with name '" + name + "'in namespace '" + namespace + "'");
				Optional<V1Secret> secret;
				secret = context.getClient()
						.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null, null)
						.getItems().stream().filter(s -> name.equals(s.getMetadata().getName())).findFirst();

				secret.ifPresent(s -> putAll(s, result));

			}
			catch (Exception e) {
				if (context.isFailFast()) {
					throw new IllegalStateException(
							"Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read secret with name: '" + name + "' in namespace: '" + namespace + "' (cause: "
						+ e.getMessage() + "). Ignoring");
			}

			String sourceName = getSourceName(name, namespace);
			return new AbstractMap.SimpleImmutableEntry<>(sourceName, result);

		};
	}

	private static Function<KubernetesClientConfigContext, Map.Entry<String, Map<String, Object>>> labeledSecret() {
		return context -> {

			Map<String, Object> result = new HashMap<>();
			Map<String, String> labels = ((LabeledSecretNormalizedSource) context.getNormalizedSource()).getLabels();
			String namespace = context.getAppNamespace();
			// name is either the concatenated labels or the concatenated names
			// of the secrets that match these labels
			String name = labels.entrySet().stream().map(en -> en.getKey() + ":" + en.getValue())
					.collect(Collectors.joining("#"));

			try {

				LOG.info("Loading Secret with labels '" + labels + "'in namespace '" + namespace + "'");
				List<V1Secret> secrets = context.getClient().listNamespacedSecret(namespace, null, null, null, null,
						createLabelsSelector(labels), null, null, null, null, null).getItems();

				name = secrets.stream().map(V1Secret::getMetadata).map(V1ObjectMeta::getName)
						.collect(Collectors.joining(Constants.PROPERTY_SOURCE_NAME_SEPARATOR));

				secrets.forEach(s -> putAll(s, result));

			}
			catch (Exception e) {
				if (context.isFailFast()) {
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

	private static void putAll(V1Secret secret, Map<String, Object> result) {
		Map<String, String> secretData = new HashMap<>();
		if (secret.getData() != null) {
			secret.getData().forEach((key, value) -> secretData.put(key, Base64.getEncoder().encodeToString(value)));
			putAll(secretData, result);
		}
	}

}
