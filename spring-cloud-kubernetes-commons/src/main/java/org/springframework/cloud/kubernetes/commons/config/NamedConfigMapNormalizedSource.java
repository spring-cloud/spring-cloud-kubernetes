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

import java.util.Objects;

/**
 * A config map source that is based on name.
 *
 * @author wind57
 */
public final class NamedConfigMapNormalizedSource extends NormalizedSource {

	private final String name;

	private final String namespace;

	private final String prefix;

	private final boolean includeProfileSpecificSources;

	private final boolean failFast;

	public NamedConfigMapNormalizedSource(String name, String namespace, String prefix,
			boolean includeProfileSpecificSources, boolean failFast) {
		super(namespace);
		this.name = name;
		this.namespace = namespace;
		this.prefix = Objects.requireNonNull(prefix);
		this.includeProfileSpecificSources = includeProfileSpecificSources;
		this.failFast = failFast;
	}

	public String getName() {
		return name;
	}

	public String getPrefix() {
		return prefix;
	}

	public boolean isIncludeProfileSpecificSources() {
		return includeProfileSpecificSources;
	}

	public boolean isFailFast() {
		return failFast;
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public String toString() {
		return "{ config-map name : '" + getName() + "', namespace : '" + getNamespace() + "', prefix : '" + getPrefix()
				+ "' }";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		NamedConfigMapNormalizedSource other = (NamedConfigMapNormalizedSource) o;
		return Objects.equals(this.getName(), other.getName())
				&& Objects.equals(this.getNamespace(), other.getNamespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, namespace);
	}

	@Override
	public NormalizedSourceType type() {
		return NormalizedSourceType.NAMED_CONFIG_MAP;
	}

	@Override
	public String target() {
		return "Config Map";
	}

}
