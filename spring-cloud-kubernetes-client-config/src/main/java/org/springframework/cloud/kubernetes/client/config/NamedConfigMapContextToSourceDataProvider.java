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

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPrefixContext;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.commons.config.Constants.PROPERTY_SOURCE_NAME_SEPARATOR;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;

/**
 * Provides an implementation of {@link KubernetesClientContextToSourceData} for a named config map.
 *
 * @author wind57
 */
final class NamedConfigMapContextToSourceDataProvider implements Supplier<KubernetesClientContextToSourceData> {

	private static final Log LOG = LogFactory.getLog(NamedConfigMapContextToSourceDataProvider.class);

	private final BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor;

	private final BiFunction<String, String, String> sourceNameMapper;

	private final Function<ConfigMapPrefixContext, SourceData> withPrefix;

	private NamedConfigMapContextToSourceDataProvider(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor,
			BiFunction<String, String, String> sourceNameMapper,
			Function<ConfigMapPrefixContext, SourceData> withPrefix) {
		this.entriesProcessor = Objects.requireNonNull(entriesProcessor);
		this.sourceNameMapper = Objects.requireNonNull(sourceNameMapper);
		this.withPrefix = Objects.requireNonNull(withPrefix);
	}

	static NamedConfigMapContextToSourceDataProvider of(
		BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor,
		BiFunction<String, String, String> sourceNameMapper,
		Function<ConfigMapPrefixContext, SourceData> withPrefix) {
		return new NamedConfigMapContextToSourceDataProvider(entriesProcessor, sourceNameMapper, withPrefix);
	}

	@Override
	public KubernetesClientContextToSourceData get() {

		return context -> {

			NamedConfigMapNormalizedSource source = (NamedConfigMapNormalizedSource) context.normalizedSource();
			String sourceName = source.name();
			// namespace has to be read from context, not from the normalized source
			String namespace = context.namespace();
			Environment environment = context.environment();
			String configMapName = sourceName != null ? sourceName : appName(environment, source).get();
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
				 * 1. read all config maps in the namespace
				 * 2. filter the ones that match user supplied name + profile specific ones
				 * 3. get an Entry that contains the data from config map + its name
				 * 4. create a single map that contains all the data from all config maps; also
				 *    create a unified name of the property source (combined config map names).
				 */
				context.client().listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null, null)
					.getItems().stream().filter(cm -> names.contains(cm.getMetadata().getName()))
					.map(cm -> new AbstractMap.SimpleEntry<>(entriesProcessor.apply(cm.getData(), environment), cm.getMetadata().getName()))
					.collect(Collectors.toList())
					.forEach(entry -> {
						LOG.info("Loaded config map with name : '" + entry.getValue()  + "' in namespace : '" + namespace + "'");
						result.putAll(entry.getKey());
						propertySourceNames.add(entry.getValue());
					});

				if (!"".equals(source.prefix())) {
					ConfigMapPrefixContext prefixContext = new ConfigMapPrefixContext(result, source.prefix(),
						namespace, propertySourceNames);
					return withPrefix.apply(prefixContext);
				}

			}
			catch (ApiException e) {
				// we could make the error tell exactly what config map we could not read,
				// this would mean separate calls to kubeapi server via the client, though.
				String message = "Unable to read ConfigMap(s) in namespace '" + namespace + "'";
				onException(source.failFast(), message, e);
			}

			String propertySourceTokens = String.join(PROPERTY_SOURCE_NAME_SEPARATOR, propertySourceNames);
			return new SourceData(sourceNameMapper.apply(propertySourceTokens, namespace), result);
		};

	}

	// unlike a Secret, users have the option not to specify the config map name in properties.
	// in such cases we will try to get it elsewhere. getApplicationName method has that logic.
	private Supplier<String> appName(Environment environment, NormalizedSource normalizedSource) {
		return () -> getApplicationName(environment, normalizedSource.name(), normalizedSource.target());
	}

}
