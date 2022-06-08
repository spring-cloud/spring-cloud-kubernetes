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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * A config map source that is based on labels.
 *
 * @author wind57
 */
public final class LabeledConfigMapNormalizedSource extends NormalizedSource {

	private final Map<String, String> labels;

	private final ConfigUtils.Prefix prefix;

	private final boolean includeProfileSpecificSources;

	public LabeledConfigMapNormalizedSource(String namespace, Map<String, String> labels, boolean failFast,
			ConfigUtils.Prefix prefix, boolean includeProfileSpecificSources) {
		super(null, namespace, failFast);
		this.labels = Collections.unmodifiableMap(Objects.requireNonNull(labels));
		this.prefix = Objects.requireNonNull(prefix);
		this.includeProfileSpecificSources = includeProfileSpecificSources;
	}

	public LabeledConfigMapNormalizedSource(String namespace, Map<String, String> labels, boolean failFast,
			boolean includeProfileSpecificSources) {
		super(null, namespace, failFast);
		this.labels = Collections.unmodifiableMap(Objects.requireNonNull(labels));
		this.prefix = ConfigUtils.Prefix.DEFAULT;
		this.includeProfileSpecificSources = includeProfileSpecificSources;
	}

	/**
	 * will return an immutable Map.
	 */
	public Map<String, String> labels() {
		return labels;
	}

	public ConfigUtils.Prefix prefix() {
		return prefix;
	}

	public boolean profileSpecificSources() {
		return this.includeProfileSpecificSources;
	}

	@Override
	public NormalizedSourceType type() {
		return NormalizedSourceType.LABELED_CONFIG_MAP;
	}

	@Override
	public String target() {
		return "configmap";
	}

	@Override
	public String toString() {
		return "{ config map labels : '" + labels() + "', namespace : '" + namespace() + "'";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LabeledConfigMapNormalizedSource other = (LabeledConfigMapNormalizedSource) o;
		return Objects.equals(labels(), other.labels()) && Objects.equals(namespace(), other.namespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(labels(), namespace());
	}

}
