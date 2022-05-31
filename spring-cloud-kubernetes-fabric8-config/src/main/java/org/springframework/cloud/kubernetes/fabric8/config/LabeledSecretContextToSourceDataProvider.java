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

import org.springframework.cloud.kubernetes.commons.config.LabeledSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.LabeledSourceData;
import org.springframework.core.env.Environment;

/**
 * Provides an implementation of {@link Fabric8ContextToSourceData} for a labeled secret.
 *
 * @author wind57
 */
final class LabeledSecretContextToSourceDataProvider implements Supplier<Fabric8ContextToSourceData> {

	private final BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor;

	private LabeledSecretContextToSourceDataProvider(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		this.entriesProcessor = Objects.requireNonNull(entriesProcessor);
	}

	static LabeledSecretContextToSourceDataProvider of(
			BiFunction<Map<String, String>, Environment, Map<String, Object>> entriesProcessor) {
		return new LabeledSecretContextToSourceDataProvider(entriesProcessor);
	}

	/*
	 * Computes a ContextSourceData (think content) for secret(s) based on some labels.
	 * There could be many secrets that are read based on incoming labels, for which we
	 * will be computing a single Map<String, Object> in the end.
	 *
	 * If there is no secret found for the provided labels, we will return an "empty"
	 * SourceData. Its name is going to be the concatenated labels mapped to an empty Map.
	 *
	 * If we find secret(s) for the provided labels, its name is going to be the
	 * concatenated secret names mapped to the data they hold as a Map.
	 */
	@Override
	public Fabric8ContextToSourceData get() {

		return context -> {

			LabeledSecretNormalizedSource source = (LabeledSecretNormalizedSource) context.normalizedSource();

			return new LabeledSourceData() {
				@Override
				public Map.Entry<Set<String>, Map<String, Object>> dataSupplier(Map<String, String> labels) {
					return Fabric8ConfigUtils.secretsDataByLabels(context.client(), context.namespace(), labels,
							context.environment(), entriesProcessor);
				}

				@Override
				public Map.Entry<Set<String>, Map<String, Object>> dataSupplier(Set<String> sourceNames) {
					return Fabric8ConfigUtils.secretsDataByName(context.client(), context.namespace(), sourceNames,
							context.environment(), entriesProcessor);
				}
			}.compute(source.labels(), source.prefix(), source.target(), source.profileSpecificSources(),
					source.failFast(), context.namespace(), context.environment().getActiveProfiles());
		};

	}

}
