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
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.PrefixContext;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.dataFromSecret;

/**
 * Provides an implementation of {@link Fabric8ContextToSourceData} for a named secret.
 *
 * @author wind57
 */
final class NamedSecretContextToSourceDataProvider implements Supplier<Fabric8ContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(LabeledSecretContextToSourceDataProvider.class);

	private final BiFunction<String, String, String> sourceNameMapper;

	private NamedSecretContextToSourceDataProvider(BiFunction<String, String, String> sourceNameFunction) {
		this.sourceNameMapper = Objects.requireNonNull(sourceNameFunction);
	}

	static NamedSecretContextToSourceDataProvider of(BiFunction<String, String, String> sourceNameFunction) {
		return new NamedSecretContextToSourceDataProvider(sourceNameFunction);
	}

	@Override
	public Fabric8ContextToSourceData get() {
		return context -> {

			NamedSecretNormalizedSource source = (NamedSecretNormalizedSource) context.normalizedSource();
			Set<String> propertySourceNames = new LinkedHashSet<>();
			propertySourceNames.add(source.name().orElseThrow());

			Map<String, Object> result = new HashMap<>();
			// error should never be thrown here, since we always expect a name
			// explicit or implicit
			String secretName = source.name().orElseThrow();
			String namespace = context.namespace();

			try {

				LOG.info("Loading Secret with name '" + secretName + "' in namespace '" + namespace + "'");
				Secret secret = context.client().secrets().inNamespace(namespace).withName(secretName).get();
				// the API is documented that it might return null
				if (secret == null) {
					LOG.warn("secret with name : " + secretName + " in namespace : " + namespace + " not found");
				}
				else {
					result = dataFromSecret(secret, namespace);

					if (!"".equals(source.prefix())) {
						PrefixContext prefixContext = new PrefixContext(result, source.prefix(), namespace,
								propertySourceNames);
						return ConfigUtils.withPrefix(prefixContext);
					}
				}

			}
			catch (Exception e) {
				String message = "Unable to read Secret with name '" + secretName + "' in namespace '" + namespace
						+ "'";
				onException(source.failFast(), message, e);
			}

			String sourceName = sourceNameMapper.apply(secretName, namespace);
			return new SourceData(sourceName, result);
		};
	}

}
