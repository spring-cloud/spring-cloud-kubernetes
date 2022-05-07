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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.PrefixContext;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import static org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils.dataFromSecret;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;

/**
 * Provides an implementation of {@link KubernetesClientContextToSourceData} for a named
 * secret.
 *
 * @author wind57
 */
final class NamedSecretContextToSourceDataProvider implements Supplier<KubernetesClientContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(NamedSecretContextToSourceDataProvider.class);

	NamedSecretContextToSourceDataProvider() {
	}

	@Override
	public KubernetesClientContextToSourceData get() {
		return context -> {

			NamedSecretNormalizedSource source = (NamedSecretNormalizedSource) context.normalizedSource();
			Set<String> propertySourceNames = new LinkedHashSet<>();
			propertySourceNames.add(source.name().orElseThrow());

			Map<String, Object> result = new HashMap<>();
			String namespace = context.namespace();
			// error should never be thrown here, since we always expect a name
			// explicit or implicit
			String name = source.name().orElseThrow();

			try {

				LOG.info("Loading Secret with name '" + name + "' in namespace '" + namespace + "'");
				Optional<V1Secret> secret;
				secret = context.client()
						.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null, null)
						.getItems().stream().filter(s -> name.equals(s.getMetadata().getName())).findFirst();

				secret.ifPresent(s -> result.putAll(dataFromSecret(s, namespace)));

				if (!"".equals(source.prefix()) && !result.isEmpty()) {
					PrefixContext prefixContext = new PrefixContext(result, source.prefix(), namespace,
							propertySourceNames);
					return ConfigUtils.withPrefix(source.target(), prefixContext);
				}

			}
			catch (Exception e) {
				String message = "Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'";
				onException(source.failFast(), message, e);
			}

			String propertySourceName = ConfigUtils.sourceName(source.target(), name, namespace);
			return new SourceData(propertySourceName, result);

		};
	}

}
