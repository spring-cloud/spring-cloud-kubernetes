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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.cloud.kubernetes.commons.config.LabeledConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.LabeledSourceData;
import org.springframework.cloud.kubernetes.commons.config.MultipleSourcesContainer;

class LabeledConfigMapContextToSourceDataProvider implements Supplier<KubernetesClientContextToSourceData> {

	LabeledConfigMapContextToSourceDataProvider() {
	}

	/*
	 * Computes a ContextSourceData (think content) for configmap(s) based on some labels.
	 * There could be many sources that are read based on incoming labels, for which we
	 * will be computing a single Map<String, Object> in the end.
	 *
	 * If there are no config maps found for the provided labels, we will return an
	 * "empty" SourceData. Its name is going to be the concatenated labels mapped to an
	 * empty Map.
	 *
	 */
	@Override
	public KubernetesClientContextToSourceData get() {

		return context -> {

			LabeledConfigMapNormalizedSource source = (LabeledConfigMapNormalizedSource) context.normalizedSource();

			return new LabeledSourceData() {
				@Override
				public MultipleSourcesContainer dataSupplier(Map<String, String> labels, Set<String> profiles) {
					return KubernetesClientConfigUtils.configMapsDataByLabels(context.client(), context.namespace(),
							labels, context.environment(), profiles);
				}

			}.compute(source.labels(), source.prefix(), source.target(), source.profileSpecificSources(),
					source.failFast(), context.namespace(), context.environment().getActiveProfiles());
		};

	}

}
