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
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.dataFromSecret;

/**
 * Provides an implementation of {@link ContextToSourceData} for a named secret.
 *
 * @author wind57
 */
final class NamedSecretContextToSourceDataProvider implements Supplier<ContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(LabeledSecretContextToSourceDataProvider.class);

	private final BiFunction<String, String, String> sourceNameMapper;

	private NamedSecretContextToSourceDataProvider(BiFunction<String, String, String> sourceNameFunction) {
		this.sourceNameMapper = Objects.requireNonNull(sourceNameFunction);
	}

	static NamedSecretContextToSourceDataProvider of(BiFunction<String, String, String> sourceNameFunction) {
		return new NamedSecretContextToSourceDataProvider(sourceNameFunction);
	}

	@Override
	public ContextToSourceData get() {
		return context -> {

			NamedSecretNormalizedSource source = (NamedSecretNormalizedSource) context.normalizedSource();

			Map<String, Object> result = new HashMap<>();
			String name = source.getName();
			String namespace = context.namespace();

			try {

				LOG.info("Loading Secret with name '" + name + "' in namespace '" + namespace + "'");
				Secret secret = context.client().secrets().inNamespace(namespace).withName(name).get();
				// the API is documented that it might return null
				if (secret == null) {
					LOG.warn("secret with name : " + name + " in namespace : " + namespace + " not found");
				}
				else {
					result = dataFromSecret(secret, namespace);
				}

			}
			catch (Exception e) {
				if (source.isFailFast()) {
					throw new IllegalStateException(
							"Unable to read Secret with name '" + name + "' in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read secret with name: '" + name + "' in namespace: '" + namespace + "' (cause: "
						+ e.getMessage() + "). Ignoring");
			}

			String sourceName = sourceNameMapper.apply(name, namespace);
			return new SourceData(sourceName, result);
		};
	}

}
