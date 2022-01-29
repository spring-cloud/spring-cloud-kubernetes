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
import java.util.Map;
import java.util.Objects;

/**
 * A secret source that is based on labels.
 *
 * @author wind57
 */
public final class LabeledSecretNormalizedSource extends NormalizedSource {

	private final Map<String, String> labels;

	private final boolean failFast;

	public LabeledSecretNormalizedSource(String namespace, boolean failFast, Map<String, String> labels) {
		super(namespace);
		this.labels = Collections.unmodifiableMap(Objects.requireNonNull(labels));
		this.failFast = failFast;
	}

	/**
	 * will return an immutable Map.
	 */
	public Map<String, String> getLabels() {
		return labels;
	}

	public boolean isFailFast() {
		return failFast;
	}

	@Override
	public String toString() {
		return "{ secret labels : '" + getLabels() + "', namespace : '" + getNamespace() + "'";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LabeledSecretNormalizedSource other = (LabeledSecretNormalizedSource) o;
		return Objects.equals(getLabels(), other.getLabels()) && Objects.equals(getNamespace(), other.getNamespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getLabels(), getNamespace());
	}

	@Override
	public NormalizedSourceType type() {
		return NormalizedSourceType.LABELED_SECRET;
	}

	@Override
	public String target() {
		return "Secret";
	}

}
