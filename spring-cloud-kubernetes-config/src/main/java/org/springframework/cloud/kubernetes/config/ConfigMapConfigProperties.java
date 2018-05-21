/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("spring.cloud.kubernetes.config")
public class ConfigMapConfigProperties extends AbstractConfigProperties {

    private static final String TARGET = "Config Map";

	private boolean enableApi = true;
	private List<String> paths = new LinkedList<>();
	private List<Source> sources = new LinkedList<>();

	public boolean isEnableApi() {
		return enableApi;
	}

	public void setEnableApi(boolean enableApi) {
		this.enableApi = enableApi;
	}

	public void setPaths(List<String> paths) {
		this.paths = paths;
	}

	public List<String> getPaths() {
		return paths;
	}

	public List<Source> getSources() {
		return sources;
	}

	public void setSources(List<Source> sources) {
		this.sources = sources;
	}

	/**
	 * @return A list of Source to use
	 * If the user has not specified any Source properties, then a single Source
	 * is constructed based on the supplied name and namespace
	 *
	 * These are the actual name/namespace pairs that are used to create a ConfigMapPropertySource
	 */
	public List<NormalizedSource> determineSources() {
		if (sources.isEmpty()) {
			return new ArrayList<NormalizedSource>() {{
				add(new NormalizedSource(name, namespace));
			}};
		}

		return sources.stream().map(s -> s.normalize(name, namespace)).collect(Collectors.toList());
	}

	@Override
    public String getConfigurationTarget() {
        return TARGET;
    }

    public static class Source {

		/**
		 * The name of the ConfigMap
		 */
		private String name;

		/**
		 * The namespace where the ConfigMap is found
		 */
		private String namespace;

		public Source() {
		}

		public Source(String name, String namespace) {
			this.name = name;
			this.namespace = namespace;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getNamespace() {
			return namespace;
		}

		public void setNamespace(String namespace) {
			this.namespace = namespace;
		}

		public boolean isEmpty() {
			return StringUtils.isEmpty(name) && StringUtils.isEmpty(namespace);
		}

		public NormalizedSource normalize(String defaultName, String defaultNamespace) {
			final String normalizedName =
				StringUtils.isEmpty(this.name) ? defaultName : this.name;
			final String normalizedNamespace =
				StringUtils.isEmpty(this.namespace) ? defaultNamespace : this.namespace;

			return new NormalizedSource(normalizedName, normalizedNamespace);
		}
	}

	static class NormalizedSource {
		private final String name;
		private final String namespace;

		public NormalizedSource(String name, String namespace) {
			this.name = name;
			this.namespace = namespace;
		}

		public String getName() {
			return name;
		}

		public String getNamespace() {
			return namespace;
		}
	}
}
