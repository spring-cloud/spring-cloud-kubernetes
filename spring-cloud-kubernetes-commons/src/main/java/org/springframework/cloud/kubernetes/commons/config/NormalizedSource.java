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

import java.util.Optional;

/**
 * Base class for Normalized Sources. It should contain all the "normalized" properties
 * that users can specify, either explicitly or implicitly.
 *
 * @author wind57
 */
public sealed abstract class NormalizedSource permits NamedSecretNormalizedSource, LabeledSecretNormalizedSource, NamedConfigMapNormalizedSource, LabeledConfigMapNormalizedSource {

	private final String namespace;

	private final String name;

	private final boolean failFast;

	protected NormalizedSource(String name, String namespace, boolean failFast) {
		this.name = name;
		this.namespace = namespace;
		this.failFast = failFast;
	}

	public final Optional<String> namespace() {
		return Optional.ofNullable(this.namespace);
	}

	// this can return an empty Optional (for a secret based on labels, for example)
	public final Optional<String> name() {
		return Optional.ofNullable(this.name);
	}

	public final boolean failFast() {
		return failFast;
	}

	/**
	 * type of this normalized source. Callers are sensitive towards the actual type
	 * specified.
	 */
	public abstract NormalizedSourceType type();

	public abstract String target();

	public abstract String toString();

	public abstract boolean equals(Object o);

	public abstract int hashCode();

}
