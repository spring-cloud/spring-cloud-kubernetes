/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.ObjectUtils;

/**
 * @author wind57
 */
interface KubernetesClientSourcesStripper {

	LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesClientSourcesStripper.class));

	static List<StrippedSourceContainer> strippedSecrets(List<V1Secret> secrets) {
		return secrets.stream()
			.map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(), secret.getMetadata().getName(),
					transform(secret.getData())))
			.toList();
	}

	static List<StrippedSourceContainer> strippedConfigMaps(List<V1ConfigMap> configMaps) {
		return configMaps.stream()
			.map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
					configMap.getMetadata().getName(), configMap.getData()))
			.toList();
	}

	static void handleApiException(ApiException e, String sourceName) {
		if (e.getCode() == 404) {
			LOG.warn("source with name : " + sourceName + " not found. Ignoring");
		}
		else {
			throw new RuntimeException(e.getResponseBody(), e);
		}
	}

	private static Map<String, String> transform(Map<String, byte[]> in) {
		return ObjectUtils.isEmpty(in) ? Map.of()
				: in.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, en -> new String(en.getValue())));
	}

}
