/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Properties for configuring Kubernetes secrets.
 *
 * @author l burgazzoli
 * @author Haytham Mohamed
 */
@ConfigurationProperties("spring.cloud.kubernetes.secrets")
public class SecretsConfigProperties extends AbstractConfigProperties {

	private static final String TARGET = "Secret";

	private boolean enableApi = false;

	private Map<String, String> labels = new HashMap<>();

	private List<String> paths = new LinkedList<>();

	private List<Source> sources = new LinkedList<>();

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

	@Override
	public String getConfigurationTarget() {
		return TARGET;
	}

	/**
	 * @return A list of Source to use If the user has not specified any Source
	 * properties, then a single Source is constructed based on the supplied name and
	 * namespace
	 *
	 * These are the actual name/namespace pairs that are used to create a
	 * SecretsPropertySource
	 */
	public List<SecretsConfigProperties.NormalizedSource> determineSources() {
		if (this.sources.isEmpty()) {
			return new ArrayList<SecretsConfigProperties.NormalizedSource>() {
				{
					add(new SecretsConfigProperties.NormalizedSource(SecretsConfigProperties.this.name,
							SecretsConfigProperties.this.namespace, SecretsConfigProperties.this.labels));
				}
			};
		}

		return this.sources.stream().map(s -> s.normalize(this.name, this.namespace, this.labels))
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
		private Map<String, String> labels = new HashMap<>();

		public Source() {
		}

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
			return StringUtils.isEmpty(this.name) && StringUtils.isEmpty(this.namespace);
		}

		public SecretsConfigProperties.NormalizedSource normalize(String defaultName, String defaultNamespace,
				Map<String, String> defaultLabels) {
			final String normalizedName = StringUtils.isEmpty(this.name) ? defaultName : this.name;
			final String normalizedNamespace = StringUtils.isEmpty(this.namespace) ? defaultNamespace : this.namespace;
			final Map<String, String> normalizedLabels = this.labels.isEmpty() ? defaultLabels : this.labels;

			return new SecretsConfigProperties.NormalizedSource(normalizedName, normalizedNamespace, normalizedLabels);
		}

	}

	static class NormalizedSource {

		private final String name;

		private final String namespace;

		private Map<String, String> labels = new HashMap<>();

		NormalizedSource(String name, String namespace, Map<String, String> labels) {
			this.name = name;
			this.namespace = namespace;
			this.labels = labels;
		}

		public String getName() {
			return this.name;
		}

		public String getNamespace() {
			return this.namespace;
		}

		public Map<String, String> getLabels() {
			return labels;
		}

	}

}
