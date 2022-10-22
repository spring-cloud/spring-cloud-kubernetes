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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;

/**
 * Config map configuration properties.
 *
 * @author Ioannis Canellos
 * @author Isik Erhan
 */
@ConfigurationProperties(ConfigMapConfigProperties.PREFIX)
public record ConfigMapConfigProperties(@DefaultValue("true") boolean enableApi, @DefaultValue List<String> paths,
		@DefaultValue List<Source> sources, @DefaultValue Map<String, String> labels,
		@DefaultValue("true") boolean enabled, String name, String namespace, boolean useNameAsPrefix,
		@DefaultValue("true") boolean includeProfileSpecificSources, boolean failFast,
		@DefaultValue RetryProperties retry) {

	/**
	 * Prefix for Kubernetes config maps configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.kubernetes.config";

	/**
	 * @return A list of config map source(s) to use.
	 */
	public List<NormalizedSource> determineSources(Environment environment) {
		if (this.sources.isEmpty()) {
			List<NormalizedSource> result = new ArrayList<>(2);
			String name = getApplicationName(environment, this.name, "ConfigMap");
			result.add(new NamedConfigMapNormalizedSource(name, this.namespace, this.failFast,
					this.includeProfileSpecificSources));

			if (!labels.isEmpty()) {
				result.add(new LabeledConfigMapNormalizedSource(this.namespace, this.labels, this.failFast,
						ConfigUtils.Prefix.DEFAULT, false));
			}
			return result;
		}

		return this.sources
				.stream().flatMap(s -> s.normalize(this.name, this.namespace, this.labels,
						this.includeProfileSpecificSources, this.failFast, this.useNameAsPrefix, environment))
				.collect(Collectors.toList());
	}

	/**
	 * Config map source.
	 * @param name The name of the ConfigMap.
	 * @param namespace The namespace where the ConfigMap is found.
	 * @param labels labels of the config map to look for against.
	 * @param explicitPrefix An explicit prefix to be used for properties.
	 * @param useNameAsPrefix Use config map name as prefix for properties.
	 * @param includeProfileSpecificSources Use profile name to append to a config map
	 * name.
	 */
	public record Source(String name, String namespace, @DefaultValue Map<String, String> labels, String explicitPrefix,
			Boolean useNameAsPrefix, Boolean includeProfileSpecificSources) {

		private Stream<NormalizedSource> normalize(String defaultName, String defaultNamespace,
				Map<String, String> defaultLabels, boolean defaultIncludeProfileSpecificSources, boolean failFast,
				boolean defaultUseNameAsPrefix, Environment environment) {

			Stream.Builder<NormalizedSource> normalizedSources = Stream.builder();

			String normalizedName = StringUtils.hasLength(this.name) ? this.name : defaultName;
			String normalizedNamespace = StringUtils.hasLength(this.namespace) ? this.namespace : defaultNamespace;
			Map<String, String> normalizedLabels = this.labels.isEmpty() ? defaultLabels : this.labels;

			String configMapName = getApplicationName(environment, normalizedName, "Config Map");

			ConfigUtils.Prefix prefix = ConfigUtils.findPrefix(this.explicitPrefix, this.useNameAsPrefix,
					defaultUseNameAsPrefix, normalizedName);

			boolean includeProfileSpecificSources = ConfigUtils.includeProfileSpecificSources(
					defaultIncludeProfileSpecificSources, this.includeProfileSpecificSources);
			NormalizedSource namedBasedSource = new NamedConfigMapNormalizedSource(configMapName, normalizedNamespace,
					failFast, prefix, includeProfileSpecificSources);
			normalizedSources.add(namedBasedSource);

			if (!normalizedLabels.isEmpty()) {
				NormalizedSource labeledBasedSource = new LabeledConfigMapNormalizedSource(normalizedNamespace, labels,
						failFast, prefix, includeProfileSpecificSources);
				normalizedSources.add(labeledBasedSource);
			}

			return normalizedSources.build();

		}

	}

}
