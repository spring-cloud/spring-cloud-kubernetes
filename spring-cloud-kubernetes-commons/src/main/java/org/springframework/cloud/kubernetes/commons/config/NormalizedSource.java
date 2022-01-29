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

/**
 * Base class for Normalized Sources. It should contain all the "normalized" properties
 * that users can specify, either explicitly or implicitly.
 *
 * @author wind57
 */
public sealed abstract class NormalizedSource permits NamedSecretNormalizedSource,LabeledSecretNormalizedSource,NamedConfigMapNormalizedSource {

	private final String namespace;

	NormalizedSource(String namespace) {
		this.namespace = namespace;
	}

	// this can return a null, which is perfectly fine.
	public String getNamespace() {
		return this.namespace;
	}

	public abstract String toString();

	public abstract boolean equals(Object o);

	public abstract int hashCode();

	/**
	 * type of this normalized source. Callers are sensitive towards the actual type
	 * specified.
	 */
	public abstract NormalizedSourceType type();

	public abstract String target();

}
