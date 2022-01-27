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

	private final String name;

	public NamedSecretNormalizedSource(String namespace, String name) {
		super(namespace);
		this.name = Objects.requireNonNull(name);
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "{ secret name : '" + getName() + "', namespace : '" + getNamespace() + "'";
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
		return Objects.equals(getName(), other.getName()) && Objects.equals(getNamespace(), other.getNamespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getName(), getNamespace());
	}

	@Override
	public NormalizedSourceType type() {
		return NormalizedSourceType.NAMED_SECRET;
	}

	@Override
	public String target() {
		return "Secret";
	}

}
