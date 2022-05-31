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

import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSourceData;
import org.springframework.core.env.Environment;

/**
 * Provides an implementation of {@link Fabric8ContextToSourceData} for a named secret.
 *
 * @author wind57
 */
final class NamedSecretContextToSourceDataProvider implements Supplier<Fabric8ContextToSourceData> {

	private final BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor;

	NamedSecretContextToSourceDataProvider(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		this.entriesProcessor = Objects.requireNonNull(entriesProcessor);
	}

	static NamedSecretContextToSourceDataProvider of(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		return new NamedSecretContextToSourceDataProvider(entriesProcessor);
	}

	@Override
	public Fabric8ContextToSourceData get() {
		return context -> {

			NamedSecretNormalizedSource source = (NamedSecretNormalizedSource) context.normalizedSource();

			return new NamedSourceData() {
				@Override
				public Map.Entry<Set<String>, Map<String, Object>> dataSupplier(Set<String> sourceNames) {
					return Fabric8ConfigUtils.secretsDataByName(context.client(), context.namespace(), sourceNames,
							context.environment(), entriesProcessor);
				}
			}.compute(source.name().orElseThrow(), source.prefix(), source.target(), source.profileSpecificSources(),
					source.failFast(), context.namespace(), context.environment().getActiveProfiles());
		};
	}

}
