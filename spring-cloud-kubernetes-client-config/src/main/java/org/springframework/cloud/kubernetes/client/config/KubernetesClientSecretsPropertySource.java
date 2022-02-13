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

package org.springframework.cloud.kubernetes.client.config;

import java.util.AbstractMap;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.*;

/**
 * @author Ryan Baxter
 * @author Isik Erhan
 */
public class KubernetesClientSecretsPropertySource extends SecretsPropertySource {
	public KubernetesClientSecretsPropertySource(SourceData sourceData) {
		super(sourceData);
	}

	private static final EnumMap<NormalizedSourceType, KubernetesClientContextToSourceData> STRATEGIES = new EnumMap<>(
		NormalizedSourceType.class);

	static {
		STRATEGIES.put(NormalizedSourceType.NAMED_SECRET, namedSecret());
		STRATEGIES.put(NormalizedSourceType.LABELED_SECRET, labeledSecret());
	}

	KubernetesClientSecretsPropertySource(KubernetesClientConfigContext context) {
		super(getSourceData(context));
	}

	private static SourceData getSourceData(KubernetesClientConfigContext context) {
		NormalizedSourceType type = context.normalizedSource().type();
		return Optional.ofNullable(STRATEGIES.get(type)).map(x -> x.apply(context))
			.orElseThrow(() -> new IllegalArgumentException("no strategy found for : " + type));
	}

	private static KubernetesClientContextToSourceData namedSecret() {
		return NamedSecretContextToSourceDataProvider.of(SecretsPropertySource::getSourceName).get();
	}

	private static KubernetesClientContextToSourceData labeledSecret() {
		return LabeledSecretContextToSourceDataProvider.of(SecretsPropertySource::getSourceName).get();
	}

}
