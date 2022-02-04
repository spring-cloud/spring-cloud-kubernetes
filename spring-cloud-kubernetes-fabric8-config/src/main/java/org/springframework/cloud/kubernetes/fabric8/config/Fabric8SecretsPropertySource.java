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
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSourceType;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.config.LabeledSecretContextToSourceDataProvider.of;

/**
 * Kubernetes property source for secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author Isik Erhan
 */
public class Fabric8SecretsPropertySource extends SecretsPropertySource {

	private static final EnumMap<NormalizedSourceType, ContextToSourceData> STRATEGIES = new EnumMap<>(NormalizedSourceType.class);

	static {
		STRATEGIES.put(NormalizedSourceType.NAMED_SECRET, namedSecret());
		STRATEGIES.put(NormalizedSourceType.LABELED_SECRET, labeledSecret());
	}

	private static final Log LOG = LogFactory.getLog(Fabric8SecretsPropertySource.class);

	public Fabric8SecretsPropertySource(Fabric8ConfigContext context) {
		super(null);
		//TODO
		//super(getSourceData(context));
	}

	private static Map.Entry<String, Map<String, Object>> getSourceData(Fabric8ConfigContext context) {
		//TODO
//		NormalizedSourceType type = context.normalizedSource().type();
//		return Optional.ofNullable(STRATEGIES.get(type)).map(x -> x.apply(context))
//				.orElseThrow(() -> new IllegalArgumentException("no strategy found for : " + type));
		return null;
	}

	private static ContextToSourceData namedSecret() {
		return null;
		//TODO
//		return context -> {
//
//			Map<String, Object> result = new HashMap<>();
//			String name = ((NamedSecretNormalizedSource) context.normalizedSource()).getName();
//			String namespace = context.namespace();
//
//			try {
//
//				LOG.info("Loading Secret with name '" + name + "' in namespace '" + namespace + "'");
//				Secret secret = context.client().secrets().inNamespace(namespace).withName(name).get();
//				// the API is documented that it might return null
//				if (secret == null) {
//					LOG.warn("secret with name : " + name + " in namespace : " + namespace + " not found");
//				}
//				else {
//					putDataFromSecret(secret, result, namespace);
//				}
//
//			}
//			catch (Exception e) {
//				if (((NamedSecretNormalizedSource) context.normalizedSource()).isFailFast()) {
//					throw new IllegalStateException(
//							"Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'", e);
//				}
//
//				LOG.warn("Can't read secret with name: '" + name + "' in namespace: '" + namespace + "' (cause: "
//						+ e.getMessage() + "). Ignoring");
//			}
//
//			String sourceName = getSourceName(name, namespace);
//			return new AbstractMap.SimpleImmutableEntry<>(sourceName, result);
//
//		};
	}

	private static ContextToSourceData labeledSecret() {
		return of(SecretsPropertySource::getSourceName).get();
	}

}
