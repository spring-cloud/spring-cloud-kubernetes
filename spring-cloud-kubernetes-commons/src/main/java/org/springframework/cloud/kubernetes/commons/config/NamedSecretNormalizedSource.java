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

import java.util.Objects;

/**
 * A secret source that is based on name.
 *
 * @author wind57
 */
public final class NamedSecretNormalizedSource extends NormalizedSource {

	private final ConfigUtils.Prefix prefix;

	private final boolean includeProfileSpecificSources;

	public NamedSecretNormalizedSource(String name, String namespace, boolean failFast, ConfigUtils.Prefix prefix,
			boolean includeProfileSpecificSources) {
		super(name, namespace, failFast);
		this.prefix = Objects.requireNonNull(prefix);
		this.includeProfileSpecificSources = includeProfileSpecificSources;
	}

	public NamedSecretNormalizedSource(String name, String namespace, boolean failFast,
			boolean includeProfileSpecificSources) {
		super(name, namespace, failFast);
		this.prefix = ConfigUtils.Prefix.DEFAULT;
		this.includeProfileSpecificSources = includeProfileSpecificSources;
	}

	public boolean profileSpecificSources() {
		return includeProfileSpecificSources;
	}

	public ConfigUtils.Prefix prefix() {
		return prefix;
	}

	@Override
	public NormalizedSourceType type() {
		return NormalizedSourceType.NAMED_SECRET;
	}

	@Override
	public String target() {
		return "secret";
	}

	@Override
	public String toString() {
		return "{ secret name : '" + name() + "', namespace : '" + namespace() + "'";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		NamedSecretNormalizedSource other = (NamedSecretNormalizedSource) o;
		return Objects.equals(name(), other.name()) && Objects.equals(namespace(), other.namespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(name(), namespace());
	}

}
