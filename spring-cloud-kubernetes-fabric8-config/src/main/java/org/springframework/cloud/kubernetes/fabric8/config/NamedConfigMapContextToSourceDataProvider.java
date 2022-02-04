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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.getConfigMapData;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * Provides an implementation of {@link ContextToSourceData} for a named config map.
 *
 * @author wind57
 */
final class NamedConfigMapContextToSourceDataProvider implements Supplier<ContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(NamedConfigMapContextToSourceDataProvider.class);

	private final BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor;

	private final BiFunction<String, String, String> sourceNameMapper;

	private NamedConfigMapContextToSourceDataProvider(
		BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor,
		BiFunction<String, String, String> sourceNameMapper) {
		this.entriesProcessor = Objects.requireNonNull(entriesProcessor);
		this.sourceNameMapper = Objects.requireNonNull(sourceNameMapper);
	}

	static NamedConfigMapContextToSourceDataProvider of(
		BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor,
		BiFunction<String, String, String> sourceNameMapper) {
		return new NamedConfigMapContextToSourceDataProvider(entriesProcessor, sourceNameMapper);
	}

	/*
	 * Computes a ContextSourceData (think content) for config map(s) based on name.
	 * There could be potentially many secrets read (we also read profile based config maps). In such a case
	 * the name of the property source is going to be the concatenated config map names, while the value
	 * is all the data that those config maps hold.
	 */
	@Override
	public ContextToSourceData get() {

		return fabric8ConfigContext -> {

			NamedConfigMapNormalizedSource source = (NamedConfigMapNormalizedSource) fabric8ConfigContext.normalizedSource();
			String configMapName = source.getName();
			String namespace = source.getNamespace();
			Environment environment = fabric8ConfigContext.environment();
			KubernetesClient client = fabric8ConfigContext.client();
			String prefix = source.getPrefix();
			String sourceName = configMapName;

			Map<String, Object> result = new HashMap<>();

			LOG.debug("Loading ConfigMap with name '" + configMapName + "' in namespace '" + namespace + "'");
			try {
				Map<String, String> data = getConfigMapData(client, namespace, configMapName);
				result.putAll(entriesProcessor.apply(data, environment));

				if (fabric8ConfigContext.environment() != null && source.isIncludeProfileSpecificSources()) {
					for (String activeProfile : environment.getActiveProfiles()) {
						String configMapNameWithProfile = configMapName + "-" + activeProfile;
						Map<String, String> dataWithProfile = getConfigMapData(client, namespace, configMapNameWithProfile);
						if (!dataWithProfile.isEmpty()) {
							sourceName = sourceName + PROPERTY_SOURCE_NAME_SEPARATOR + configMapNameWithProfile;
							result.putAll(entriesProcessor.apply(dataWithProfile, environment));
						}
					}
				}

				if (!"".equals(prefix)) {
					Map<String, Object> withPrefix = CollectionUtils.newHashMap(result.size());
					result.forEach((key, value) -> withPrefix.put(prefix + "." + key, value));
					return new SourceData(sourceNameMapper.apply(sourceName, namespace), withPrefix);
				}

			}
			catch (Exception e) {
				if (source.isFailFast()) {
					throw new IllegalStateException(
						"Unable to read ConfigMap with name '" + configMapName + "' in namespace '" + namespace + "'", e);
				}

				LOG.warn("Can't read configMap with name: [" + configMapName + "] in namespace: [" + namespace + "]. Ignoring.", e);
			}

			return new SourceData(sourceNameMapper.apply(sourceName, namespace), result);
		};

	}
}
