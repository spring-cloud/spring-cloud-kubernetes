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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.AbstractMap;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSourceType;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;

/**
 * Kubernetes property source for secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author Isik Erhan
 */
public class Fabric8SecretsPropertySource extends SecretsPropertySource {

	private static final EnumMap<NormalizedSourceType, Function<Fabric8ConfigContext, Map.Entry<String, Map<String, Object>>>> STRATEGIES = new EnumMap<>(
			NormalizedSourceType.class);

	static {
		STRATEGIES.put(NormalizedSourceType.NAMED_SECRET, namedSecret());
		STRATEGIES.put(NormalizedSourceType.LABELED_SECRET, labeledSecret());
	}

	private static final Log LOG = LogFactory.getLog(Fabric8SecretsPropertySource.class);

	public Fabric8SecretsPropertySource(Fabric8ConfigContext context) {
		super(getSourceData(context));
	}

	private static Map.Entry<String, Map<String, Object>> getSourceData(Fabric8ConfigContext context) {
		NormalizedSourceType type = context.getNormalizedSource().type();
		return Optional.ofNullable(STRATEGIES.get(type)).map(x -> x.apply(context))
				.orElseThrow(() -> new IllegalArgumentException("no strategy found for : " + type));
	}

	private static Function<Fabric8ConfigContext, Map.Entry<String, Map<String, Object>>> namedSecret() {
		return context -> {

			Map<String, Object> result = new HashMap<>();
			String name = ((NamedSecretNormalizedSource) context.getNormalizedSource()).getName();
			String namespace = context.getAppNamespace();

			try {

				LOG.info("Loading Secret with name '" + name + "' in namespace '" + namespace + "'");
				Secret secret = context.getClient().secrets().inNamespace(namespace).withName(name).get();
				// the API is documented that it might return null
				if (secret == null) {
					LOG.warn("secret with name : " + name + " in namespace : " + namespace + " not found");
				}
				else {
					putDataFromSecret(secret, result, namespace);
				}

			}
			catch (Exception e) {
				if (context.isFailFast()) {
					throw new IllegalStateException(
							"Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read secret with name: '" + name + "' in namespace: '" + namespace + "' (cause: "
						+ e.getMessage() + "). Ignoring");
			}

			return new AbstractMap.SimpleImmutableEntry<>(name, result);

		};
	}

	private static Function<Fabric8ConfigContext, Map.Entry<String, Map<String, Object>>> labeledSecret() {
		return context -> {

			Map<String, Object> result = new HashMap<>();
			Map<String, String> labels = ((LabeledSecretNormalizedSource) context.getNormalizedSource()).getLabels();
			String namespace = context.getAppNamespace();
			// name is either the concatenated labels or the concatenated names
			// of the secrets that match these labels
			String name = labels.entrySet().stream().map(en -> en.getKey() + ":" + en.getValue())
					.collect(Collectors.joining("#"));

			try {

				LOG.info("Loading Secret with lables '" + labels + "' in namespace '" + namespace + "'");
				List<Secret> secrets = context.getClient().secrets().inNamespace(namespace).withLabels(labels).list()
						.getItems();

				if (!secrets.isEmpty()) {
					secrets.forEach(s -> putDataFromSecret(s, result, namespace));
					name = secrets.stream().map(Secret::getMetadata).map(ObjectMeta::getName)
							.collect(Collectors.joining(Constants.PROPERTY_SOURCE_NAME_SEPARATOR));
				}

			}
			catch (Exception e) {
				if (context.isFailFast()) {
					throw new IllegalStateException(
							"Unable to read Secret with labels [" + labels + "] in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read secret with labels [" + labels + "] in namespace: '" + namespace + "' (cause: "
						+ e.getMessage() + "). Ignoring");
			}

			return new AbstractMap.SimpleImmutableEntry<>(name, result);
		};
	}

	private static void putDataFromSecret(Secret secret, Map<String, Object> result, String namespace) {
		LOG.debug("reading secret with name : " + secret.getMetadata().getName() + " in namespace : " + namespace);
		putAll(secret.getData(), result);
	}

}
