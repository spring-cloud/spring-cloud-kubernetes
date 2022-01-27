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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
public class SecretsConfigProperties extends AbstractConfigProperties {

	/**
	 * Prefix for Kubernetes secrets configuration properties.
	 */
	public static final String PREFIX = "spring.cloud.kubernetes.secrets";

	private boolean enableApi = false;

	private Map<String, String> labels = Collections.emptyMap();

	private List<String> paths = Collections.emptyList();

	private List<Source> sources = Collections.emptyList();

	public boolean isEnableApi() {
		return this.enableApi;
	}

	public void setEnableApi(boolean enableApi) {
		this.enableApi = enableApi;
	}

	public Map<String, String> getLabels() {
		return this.labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	public List<String> getPaths() {
		return this.paths;
	}

	public void setPaths(List<String> paths) {
		this.paths = paths;
	}

	public List<Source> getSources() {
		return sources;
	}

	public void setSources(List<Source> sources) {
		this.sources = sources;
	}

	/**
	 * @return A list of Source to use. If the user has not specified any Source
	 * properties, then a single Source is constructed based on the supplied name and
	 * namespace.
	 *
	 * These are the actual name/namespace pairs that are used to create a
	 * SecretsPropertySource
	 */
	public List<NormalizedSource> determineSources(Environment environment) {
		if (this.sources.isEmpty()) {

			List<NormalizedSource> result = new ArrayList<>(2);
			result.add(new NamedSecretNormalizedSource(this.namespace,
					getApplicationName(environment, this.name, "Secret")));

			if (!labels.isEmpty()) {
				result.add(new LabeledSecretNormalizedSource(this.namespace, this.labels));
			}
			return result;
		}

		return this.sources.stream().flatMap(s -> s.normalize(this.name, this.namespace, this.labels, environment))
				.collect(Collectors.toList());
	}

	public static class Source {

		/**
		 * The name of the Secret.
		 */
		private String name;

		/**
		 * The namespace where the Secret is found.
		 */
		private String namespace;

		/**
		 * The labels of the Secret to find.
		 */
		private Map<String, String> labels = Collections.emptyMap();

		public Source() {
		}

		@Deprecated
		public Source(String name, String namespace, Map<String, String> labels) {
			this.name = name;
			this.namespace = namespace;
			this.labels = labels;
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

		public void setLabels(Map<String, String> labels) {
			this.labels = labels;
		}

		public Map<String, String> getLabels() {
			return this.labels;
		}

		public boolean isEmpty() {
			return !StringUtils.hasLength(this.name) && !StringUtils.hasLength(this.namespace);
		}

		public Stream<NormalizedSource> normalize(String defaultName, String defaultNamespace,
				Map<String, String> defaultLabels, Environment environment) {

			Stream.Builder<NormalizedSource> normalizedSources = Stream.builder();

			String normalizedName = StringUtils.hasLength(this.name) ? this.name : defaultName;
			String normalizedNamespace = StringUtils.hasLength(this.namespace) ? this.namespace : defaultNamespace;
			Map<String, String> normalizedLabels = this.labels.isEmpty() ? defaultLabels : this.labels;

			String secretName = getApplicationName(environment, normalizedName, "Secret");
			NormalizedSource nameBasedSource = new NamedSecretNormalizedSource(normalizedNamespace, secretName);
			normalizedSources.add(nameBasedSource);

			if (!normalizedLabels.isEmpty()) {
				NormalizedSource labelsBasedSource = new LabeledSecretNormalizedSource(normalizedNamespace, labels);
				normalizedSources.add(labelsBasedSource);
			}

			return normalizedSources.build();
		}

	}

}
