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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSourceData;
import org.springframework.core.env.Environment;

/**
 * Provides an implementation of {@link Fabric8ContextToSourceData} for a named config
 * map.
 *
 * @author wind57
 */
final class NamedConfigMapContextToSourceDataProvider implements Supplier<Fabric8ContextToSourceData> {

	private final BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor;

	private NamedConfigMapContextToSourceDataProvider(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		this.entriesProcessor = Objects.requireNonNull(entriesProcessor);
	}

	static NamedConfigMapContextToSourceDataProvider of(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		return new NamedConfigMapContextToSourceDataProvider(entriesProcessor);
	}

	/*
	 * Computes a ContextToSourceData (think content) for config map(s) based on name.
	 * There could be potentially many config maps read (we also read profile based
	 * sources). In such a case the name of the property source is going to be the
	 * concatenated config map names, while the value is all the data that those config
	 * maps hold.
	 */
	@Override
	public Fabric8ContextToSourceData get() {

		return context -> {

			NamedConfigMapNormalizedSource source = (NamedConfigMapNormalizedSource) context.normalizedSource();

			return new NamedSourceData() {
				@Override
				public Map.Entry<Set<String>, Map<String, Object>> dataSupplier(Set<String> sourceNames) {
					return Fabric8ConfigUtils.configMapsDataByName(context.client(), context.namespace(), sourceNames,
							context.environment(), entriesProcessor);
				}
			}.compute(source.name().orElseThrow(), source.prefix(), source.target(), source.profileSpecificSources(),
					source.failFast(), context.namespace(), context.environment().getActiveProfiles());
		};

	}

}
