/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WatcherUtilTests {

	@Test
	void configMapWithRequiredLabelIsAccepted() {
		V1ConfigMap configMap = configMap(Map.of(ConfigurationWatcherConfigurationProperties.CONFIG_MAP_LABEL, "true"));

		boolean accepted = WatcherUtil.isSpringCloudKubernetes(configMap,
				ConfigurationWatcherConfigurationProperties.CONFIG_MAP_LABEL);

		assertThat(accepted).isTrue();
	}

	@Test
	void configMapWithoutRequiredLabelIsRejected() {
		V1ConfigMap configMap = configMap(Map.of());

		boolean accepted = WatcherUtil.isSpringCloudKubernetes(configMap,
				ConfigurationWatcherConfigurationProperties.CONFIG_MAP_LABEL);

		assertThat(accepted).isFalse();
	}

	private static V1ConfigMap configMap(Map<String, String> labels) {
		return new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMeta().name("my-configmap").labels(labels))
			.build();
	}

}
