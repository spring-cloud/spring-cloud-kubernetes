/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
final class KubernetesDiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesDiscoveryClientUtils.class));

	private KubernetesDiscoveryClientUtils() {

	}

	static boolean matchesServiceLabels(V1Service service, KubernetesDiscoveryProperties properties) {

		Map<String, String> propertiesServiceLabels = properties.serviceLabels();
		Map<String, String> serviceLabels = Optional.ofNullable(service.getMetadata()).map(V1ObjectMeta::getLabels)
				.orElse(Map.of());

		if (propertiesServiceLabels.isEmpty()) {
			LOG.debug(() -> "service labels from properties are empty, service with name : '"
					+ service.getMetadata().getName() + "' will match");
			return true;
		}

		if (serviceLabels.isEmpty()) {
			LOG.debug(() -> "service with name : '" + service.getMetadata().getName() + "' does not have labels");
			return false;
		}

		LOG.debug(() -> "Service labels from properties : " + properties.serviceLabels());
		LOG.debug(() -> "Service labels from service : " + service.getMetadata().getLabels());

		return serviceLabels.keySet().containsAll(propertiesServiceLabels.keySet());

	}

}
