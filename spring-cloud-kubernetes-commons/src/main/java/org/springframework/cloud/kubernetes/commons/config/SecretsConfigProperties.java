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
 * Properties for configuring Kubernetes secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 * @author Isik Erhan
 */
@ConfigurationProperties(SecretsConfigProperties.PREFIX)
public record SecretsConfigProperties(boolean enableApi, @DefaultValue Map<String, String> labels,
		@DefaultValue List<String> paths, @DefaultValue List<Source> sources, @DefaultValue("true") boolean enabled,
		String name, String namespace, boolean useNameAsPrefix,
		@DefaultValue("true") boolean includeProfileSpecificSources, boolean failFast,
		@DefaultValue RetryProperties retry) {

	/**
	 * Prefix for Kubernetes secrets configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.kubernetes.secrets";

	/**
	 * @return A list of Secret source(s) to use.
	 */
	public List<NormalizedSource> determineSources(Environment environment) {
		if (this.sources.isEmpty()) {
			List<NormalizedSource> result = new ArrayList<>(2);
			String name = getApplicationName(environment, this.name, "Secret");
			result.add(new NamedSecretNormalizedSource(name, this.namespace, this.failFast,
					this.includeProfileSpecificSources));

			if (!labels.isEmpty()) {
				result.add(new LabeledSecretNormalizedSource(this.namespace, this.labels, this.failFast,
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
	 * @param name The name of the Secret.
	 * @param namespace The namespace where the Secret is found.
	 * @param labels The labels of the Secret to find.
	 * @param explicitPrefix An explicit prefix to be used for properties.
	 * @param useNameAsPrefix Use secret name as prefix for properties.
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

			String secretName = getApplicationName(environment, normalizedName, "Secret");

			ConfigUtils.Prefix prefix = ConfigUtils.findPrefix(this.explicitPrefix, this.useNameAsPrefix,
					defaultUseNameAsPrefix, normalizedName);

			boolean includeProfileSpecificSources = ConfigUtils.includeProfileSpecificSources(
					defaultIncludeProfileSpecificSources, this.includeProfileSpecificSources);
			NormalizedSource namedBasedSource = new NamedSecretNormalizedSource(secretName, normalizedNamespace,
					failFast, prefix, includeProfileSpecificSources);
			normalizedSources.add(namedBasedSource);

			if (!normalizedLabels.isEmpty()) {
				NormalizedSource labeledBasedSource = new LabeledSecretNormalizedSource(normalizedNamespace, labels,
						failFast, prefix, includeProfileSpecificSources);
				normalizedSources.add(labeledBasedSource);
			}

			return normalizedSources.build();
		}

	}

}
