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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.List;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;

import org.springframework.cloud.kubernetes.commons.config.StrippedSourceContainer;

/**
 * @author wind57
 */
interface Fabric8SourcesStripper {

	static List<StrippedSourceContainer> strippedConfigMaps(List<ConfigMap> configMaps) {
		return configMaps.stream()
			.map(configMap -> new StrippedSourceContainer(configMap.getMetadata().getLabels(),
					configMap.getMetadata().getName(), configMap.getData()))
			.toList();
	}

	static List<StrippedSourceContainer> strippedSecrets(List<Secret> secrets) {
		return secrets.stream()
			.map(secret -> new StrippedSourceContainer(secret.getMetadata().getLabels(), secret.getMetadata().getName(),
					secret.getData()))
			.toList();
	}

}
