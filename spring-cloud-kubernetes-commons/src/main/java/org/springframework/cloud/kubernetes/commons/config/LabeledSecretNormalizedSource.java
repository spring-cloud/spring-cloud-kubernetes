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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.style.ToStringCreator;

/**
 * A secret source that is based on labels.
 *
 * @author wind57
 */
public final class LabeledSecretNormalizedSource extends NormalizedSource {

	private final Map<String, String> labels;

	private final ConfigUtils.Prefix prefix;

	public LabeledSecretNormalizedSource(String namespace, Map<String, String> labels, boolean failFast,
			ConfigUtils.Prefix prefix, LinkedHashSet<StrictProfile> profiles, boolean strict) {
		super(null, namespace, failFast, profiles, strict);
		this.labels = Collections.unmodifiableMap(Objects.requireNonNull(labels));
		this.prefix = Objects.requireNonNull(prefix);
	}

	public LabeledSecretNormalizedSource(String namespace, Map<String, String> labels, boolean failFast,
			LinkedHashSet<StrictProfile> profiles, boolean strict) {
		super(null, namespace, failFast, profiles, strict);
		this.labels = Collections.unmodifiableMap(Objects.requireNonNull(labels));
		this.prefix = ConfigUtils.Prefix.DEFAULT;
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

	@Override
	public NormalizedSourceType type() {
		return NormalizedSourceType.LABELED_SECRET;
	}

	@Override
	public String target() {
		return "secret";
	}

	@Override
	public String toString() {
		ToStringCreator creator = new ToStringCreator(this);
		creator.append("labels", labels());
		creator.append("namespace", namespace());
		creator.append("failFast", failFast());
		creator.append("prefix", prefix());
		creator.append("profiles-strictness", profiles());
		creator.append("strict", strict());

		return creator.toString();
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
		return Objects.equals(labels(), other.labels()) && Objects.equals(namespace(), other.namespace());
	}

	@Override
	public int hashCode() {
		return Objects.hash(labels(), namespace());
	}

}
