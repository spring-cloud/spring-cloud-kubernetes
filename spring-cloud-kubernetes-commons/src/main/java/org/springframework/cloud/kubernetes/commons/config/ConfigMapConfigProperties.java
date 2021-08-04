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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Config map configuration properties.
 *
 * @author Ioannis Canellos
 */
@ConfigurationProperties("spring.cloud.kubernetes.config")
public class ConfigMapConfigProperties extends AbstractConfigProperties {

	private boolean enableApi = true;

	private List<String> paths = Collections.emptyList();

	private List<Source> sources = Collections.emptyList();

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

	/**
	 * @return A list of Source to use. If the user has not specified any Source
	 * properties, then a single Source is constructed based on the supplied name and
	 * namespace.
	 *
	 * These are the actual name/namespace pairs that are used to create a
	 * ConfigMapPropertySource.
	 */
	public List<NormalizedSource> determineSources() {
		if (this.sources.isEmpty()) {
			return Collections.singletonList(new NormalizedSource(name, namespace));
		}

		return sources.stream().map(s -> s.normalize(name, namespace)).collect(Collectors.toList());
	}

	@Override
	public String getConfigurationTarget() {
		return "Config Map";
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

		public Source() {

		}

		public Source(String name, String namespace) {
			this.name = name;
			this.namespace = namespace;
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

		public boolean isEmpty() {
			return !StringUtils.hasLength(this.name) && !StringUtils.hasLength(this.namespace);
		}

		public NormalizedSource normalize(String defaultName, String defaultNamespace) {
			String normalizedName = StringUtils.hasLength(this.name) ? this.name : defaultName;
			String normalizedNamespace = StringUtils.hasLength(this.namespace) ? this.namespace : defaultNamespace;
			return new NormalizedSource(normalizedName, normalizedNamespace);
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

	public static class NormalizedSource {

		private final String name;

		private final String namespace;

		NormalizedSource(String name, String namespace) {
			this.name = name;
			this.namespace = namespace;
		}

		public String getName() {
			return this.name;
		}

		public String getNamespace() {
			return this.namespace;
		}

		@Override
		public String toString() {
			return "{ config-map name : '" + name + "', namespace : '" + namespace + "' }";
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			NormalizedSource other = (NormalizedSource) o;
			return Objects.equals(this.name, other.name) && Objects.equals(this.namespace, other.namespace);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, namespace);
		}

	}

}
