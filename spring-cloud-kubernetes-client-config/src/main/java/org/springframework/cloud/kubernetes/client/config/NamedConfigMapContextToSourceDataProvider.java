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

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.PrefixContext;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;

/**
 * Provides an implementation of {@link KubernetesClientContextToSourceData} for a named
 * config map.
 *
 * @author wind57
 */
final class NamedConfigMapContextToSourceDataProvider implements Supplier<KubernetesClientContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(NamedConfigMapContextToSourceDataProvider.class);

	private final BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor;

	private NamedConfigMapContextToSourceDataProvider(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		this.entriesProcessor = Objects.requireNonNull(entriesProcessor);
	}

	static NamedConfigMapContextToSourceDataProvider of(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		return new NamedConfigMapContextToSourceDataProvider(entriesProcessor);
	}

	@Override
	public KubernetesClientContextToSourceData get() {

		return context -> {

			NamedConfigMapNormalizedSource source = (NamedConfigMapNormalizedSource) context.normalizedSource();
			// namespace has to be read from context, not from the normalized source
			String namespace = context.namespace();
			Environment environment = context.environment();
			String configMapName = source.name().orElseThrow();
			Set<String> propertySourceNames = new LinkedHashSet<>();
			propertySourceNames.add(configMapName);

			Map<String, Object> result = new HashMap<>();
			try {
				Set<String> names = new HashSet<>();
				names.add(configMapName);
				if (environment != null && source.profileSpecificSources()) {
					for (String activeProfile : environment.getActiveProfiles()) {
						names.add(configMapName + "-" + activeProfile);
					}
				}

				/*
				 * 1. read all config maps in the namespace 2. filter the ones that match
				 * user supplied name + profile specific ones 3. get an Entry that
				 * contains the data from config map + its name 4. create a single map
				 * that contains all the data from all config maps; also create a unified
				 * name of the property source (combined config map names).
				 */
				context.client()
						.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null, null)
						.getItems().stream().filter(cm -> names.contains(cm.getMetadata().getName()))
						.map(cm -> new AbstractMap.SimpleEntry<>(entriesProcessor.apply(cm.getData(), environment),
								cm.getMetadata().getName()))
						.collect(Collectors.toList()).forEach(entry -> {
							LOG.info("Loaded config map with name : '" + entry.getValue() + "' in namespace : '"
									+ namespace + "'");
							result.putAll(entry.getKey());
							propertySourceNames.add(entry.getValue());
						});

				if (source.prefix() != ConfigUtils.Prefix.DEFAULT) {
					// since we are in a named source, calling get on the supplier is safe
					String prefix = source.prefix().prefixProvider().get();
					PrefixContext prefixContext = new PrefixContext(result, prefix, namespace, propertySourceNames);
					return ConfigUtils.withPrefix(source.target(), prefixContext);
				}

			}
			catch (ApiException e) {
				// we could make the error tell exactly what config map we could not read,
				// this would mean separate calls to kubeapi server via the client,
				// though.
				String message = "Unable to read ConfigMap(s) in namespace '" + namespace + "'";
				onException(source.failFast(), message, e);
			}

			String propertySourceTokens = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, propertySourceNames);
			return new SourceData(ConfigUtils.sourceName(source.target(), propertySourceTokens, namespace), result);
		};

	}

}
