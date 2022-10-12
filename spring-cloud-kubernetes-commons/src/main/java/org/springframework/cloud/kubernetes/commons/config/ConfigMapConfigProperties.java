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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
public class ConfigMapConfigProperties extends AbstractConfigProperties {

	/**
	 * Prefix for Kubernetes config maps configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.kubernetes.config";

	private boolean enableApi = true;

	private List<String> paths = Collections.emptyList();

	private List<Source> sources = Collections.emptyList();

	private Map<String, String> labels = Collections.emptyMap();

	public boolean isEnableApi() {
		return this.enableApi;
	}

	public void setEnableApi(boolean enableApi) {
		this.enableApi = enableApi;
	}

	public List<String> getPaths() {
		return this.paths;
	}

	public void setPaths(List<String> paths) {
		this.paths = paths;
	}

	public List<Source> getSources() {
		return this.sources;
	}

	public void setSources(List<Source> sources) {
		this.sources = sources;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

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

	// because of this current issue
	// https://github.com/spring-projects/spring-boot/issues/32660
	// we need this workaround
	public static ConfigMapConfigProperties fromSelfAndRetry(ConfigMapConfigProperties configMapConfigProperties,
			RetryProperties retryProperties) {
		ConfigMapConfigProperties properties = new ConfigMapConfigProperties();
		properties.setEnableApi(configMapConfigProperties.isEnableApi());
		properties.setPaths(configMapConfigProperties.getPaths());
		properties.setSources(configMapConfigProperties.getSources());
		properties.setLabels(configMapConfigProperties.getLabels());

		properties.setEnabled(configMapConfigProperties.isEnabled());
		properties.setName(configMapConfigProperties.getName());
		properties.setNamespace(configMapConfigProperties.getNamespace());
		properties.setUseNameAsPrefix(configMapConfigProperties.isUseNameAsPrefix());
		properties.setIncludeProfileSpecificSources(configMapConfigProperties.isIncludeProfileSpecificSources());
		properties.setFailFast(configMapConfigProperties.isFailFast());

		properties.setRetry(retryProperties);

		return properties;
	}

	/**
	 * Config map source.
	 */
	public static class Source {

		/**
		 * The name of the ConfigMap.
		 */
		private String name;

		/**
		 * The namespace where the ConfigMap is found.
		 */
		private String namespace;

		/**
		 * labels of the config map to look for against.
		 */
		private Map<String, String> labels = Collections.emptyMap();

		/**
		 * An explicit prefix to be used for properties.
		 */
		private String explicitPrefix;

		/**
		 * Use config map name as prefix for properties. Can't be a primitive, we need to
		 * know if it was explicitly set or not
		 */
		private Boolean useNameAsPrefix;

		/**
		 * Use profile name to append to a config map name. Can't be a primitive, we need
		 * to know if it was explicitly set or not
		 */
		protected Boolean includeProfileSpecificSources;

		public Source() {

		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getNamespace() {
			return this.namespace;
		}

		public void setNamespace(String namespace) {
			this.namespace = namespace;
		}

		public Boolean isUseNameAsPrefix() {
			return useNameAsPrefix;
		}

		public Boolean getUseNameAsPrefix() {
			return useNameAsPrefix;
		}

		public void setUseNameAsPrefix(Boolean useNameAsPrefix) {
			this.useNameAsPrefix = useNameAsPrefix;
		}

		public String getExplicitPrefix() {
			return explicitPrefix;
		}

		public void setExplicitPrefix(String explicitPrefix) {
			this.explicitPrefix = explicitPrefix;
		}

		public Boolean getIncludeProfileSpecificSources() {
			return includeProfileSpecificSources;
		}

		public void setIncludeProfileSpecificSources(Boolean includeProfileSpecificSources) {
			this.includeProfileSpecificSources = includeProfileSpecificSources;
		}

		public Map<String, String> getLabels() {
			return labels;
		}

		public void setLabels(Map<String, String> labels) {
			this.labels = labels;
		}

		public boolean isEmpty() {
			return !StringUtils.hasLength(this.name) && !StringUtils.hasLength(this.namespace);
		}

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

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Source other = (Source) o;
			return Objects.equals(this.name, other.name) && Objects.equals(this.namespace, other.namespace);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, namespace);
		}

	}

}
