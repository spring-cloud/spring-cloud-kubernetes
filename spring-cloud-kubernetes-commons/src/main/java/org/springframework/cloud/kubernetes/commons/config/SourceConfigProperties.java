/*
 * Copyright 2013-present the original author or authors.
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
import java.util.stream.Stream;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.Prefix;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.findPrefix;
import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.getApplicationName;
import static org.springframework.util.StringUtils.hasLength;

/**
 * @author wind57
 */
public abstract sealed class SourceConfigProperties permits ConfigMapConfigProperties, SecretsConfigProperties {

	private final boolean enabled;

	private final List<Source> sources;

	private final Map<String, String> labels;

	private final String name;

	private final String namespace;

	private final boolean useNameAsPrefix;

	private final boolean includeProfileSpecificSources;

	private final boolean failFast;

	private final RetryProperties retry;

	private final ReadType readType;

	SourceConfigProperties(boolean enabled, List<Source> sources, Map<String, String> labels, String name,
			String namespace, boolean useNameAsPrefix, boolean includeProfileSpecificSources, boolean failFast,
			RetryProperties retry, ReadType readType) {

		this.enabled = enabled;
		this.sources = sources;
		this.labels = labels;
		this.name = name;
		this.namespace = namespace;
		this.useNameAsPrefix = useNameAsPrefix;
		this.includeProfileSpecificSources = includeProfileSpecificSources;
		this.failFast = failFast;
		this.retry = retry;
		this.readType = readType;
	}

	public boolean enabled() {
		return enabled;
	}

	public List<Source> sources() {
		return sources;
	}

	public Map<String, String> labels() {
		return labels;
	}

	public String name() {
		return name;
	}

	public String namespace() {
		return namespace;
	}

	public boolean useNameAsPrefix() {
		return useNameAsPrefix;
	}

	public boolean includeProfileSpecificSources() {
		return includeProfileSpecificSources;
	}

	public boolean failFast() {
		return failFast;
	}

	public RetryProperties retry() {
		return retry;
	}

	public ReadType readType() {
		return readType;
	}

	protected final List<NormalizedSource> determineSources(SourceType sourceType, Environment environment) {
		if (sources().isEmpty()) {
			List<NormalizedSource> result = new ArrayList<>(2);
			String name = getApplicationName(environment, name(), sourceType.name());
			NormalizedSource normalizedSource;
			if (sourceType == SourceType.CONFIGMAP) {
				normalizedSource = new NamedConfigMapNormalizedSource(name, namespace(), failFast(),
						includeProfileSpecificSources());
			}
			else {
				normalizedSource = new NamedSecretNormalizedSource(name, this.namespace, this.failFast,
						this.includeProfileSpecificSources);
			}
			result.add(normalizedSource);

			if (!labels().isEmpty()) {
				NormalizedSource labeledSource;
				if (sourceType == SourceType.CONFIGMAP) {
					labeledSource = new LabeledConfigMapNormalizedSource(namespace(), labels(), failFast(),
							ConfigUtils.Prefix.DEFAULT, false);
				}
				else {
					labeledSource = new LabeledSecretNormalizedSource(this.namespace, this.labels, this.failFast,
							ConfigUtils.Prefix.DEFAULT);
				}
				result.add(labeledSource);
			}
			return result;
		}

		return sources().stream()
			.flatMap(s -> s.normalize(sourceType, name(), namespace(), labels(), includeProfileSpecificSources(),
					failFast(), useNameAsPrefix(), environment))
			.toList();
	}

	/**
	 * Config map source.
	 *
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

		Stream<NormalizedSource> normalize(SourceType sourceType, String defaultName, String defaultNamespace,
				Map<String, String> defaultLabels, boolean defaultIncludeProfileSpecificSources, boolean failFast,
				boolean defaultUseNameAsPrefix, Environment environment) {

			Stream.Builder<NormalizedSource> normalizedSources = Stream.builder();

			String normalizedName = hasLength(name) ? name : defaultName;
			String normalizedNamespace = hasLength(namespace) ? namespace : defaultNamespace;
			Map<String, String> normalizedLabels = labels.isEmpty() ? defaultLabels : labels;

			String sourceName = getApplicationName(environment, normalizedName, sourceType.name());

			Prefix prefix = findPrefix(explicitPrefix, useNameAsPrefix, defaultUseNameAsPrefix, normalizedName);

			boolean includeProfileSpecificSources = ConfigUtils.includeProfileSpecificSources(
					defaultIncludeProfileSpecificSources, this.includeProfileSpecificSources);

			NormalizedSource namedSource;
			if (sourceType == SourceType.CONFIGMAP) {
				namedSource = new NamedConfigMapNormalizedSource(sourceName, normalizedNamespace, failFast, prefix,
						includeProfileSpecificSources);
			}
			else {
				namedSource = new NamedSecretNormalizedSource(sourceName, normalizedNamespace, failFast, prefix,
						includeProfileSpecificSources);
			}
			normalizedSources.add(namedSource);

			if (!normalizedLabels.isEmpty()) {
				NormalizedSource labeledSource;
				if (sourceType == SourceType.CONFIGMAP) {
					labeledSource = new LabeledConfigMapNormalizedSource(normalizedNamespace, labels, failFast, prefix,
							includeProfileSpecificSources);
				}
				else {
					labeledSource = new LabeledSecretNormalizedSource(normalizedNamespace, labels, failFast, prefix);
				}
				normalizedSources.add(labeledSource);

			}

			return normalizedSources.build();

		}
	}

}
