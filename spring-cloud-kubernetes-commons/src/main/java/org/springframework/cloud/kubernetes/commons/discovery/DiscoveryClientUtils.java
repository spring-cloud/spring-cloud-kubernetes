/*
 * Copyright 2019-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import org.apache.commons.logging.LogFactory;
import org.springframework.core.log.LogAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.keysWithPrefix;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SERVICE_TYPE;

/**
 * @author wind57
 */
public final class DiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(DiscoveryClientUtils.class));

	private DiscoveryClientUtils() {
		throw new AssertionError("no instance provided");
	}

	/**
	 * This adds the following metadata. <pre>
	 *     - labels (if requested)
	 *     - annotations (if requested)
	 *     - ports (if requested)
	 *     - namespace
	 *     - service type
	 * </pre>
	 */
	public static Map<String, String> serviceMetadata(String serviceId, Map<String, String> serviceLabels,
			Map<String, String> serviceAnnotations, Map<String, String> portsData,
			KubernetesDiscoveryProperties properties, String namespace, String serviceType) {
		Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = properties.metadata();
		if (metadataProps.addLabels()) {
			Map<String, String> labelMetadata = keysWithPrefix(serviceLabels, metadataProps.labelsPrefix());
			LOG.debug(() -> "Adding labels metadata: " + labelMetadata + " for serviceId: " + serviceId);
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.addAnnotations()) {
			Map<String, String> annotationMetadata = keysWithPrefix(serviceAnnotations,
				metadataProps.annotationsPrefix());
			LOG.debug(() -> "Adding annotations metadata: " + annotationMetadata + " for serviceId: " + serviceId);
			serviceMetadata.putAll(annotationMetadata);
		}

		if (metadataProps.addPorts()) {
			Map<String, String> portMetadata = keysWithPrefix(portsData, properties.metadata().portsPrefix());
			if (!portMetadata.isEmpty()) {
				LOG.debug(() -> "Adding port metadata: " + portMetadata + " for serviceId : " + serviceId);
			}
			serviceMetadata.putAll(portMetadata);
		}

		serviceMetadata.put(NAMESPACE_METADATA_KEY, namespace);
		serviceMetadata.put(SERVICE_TYPE, serviceType);
		return serviceMetadata;
	}

}
