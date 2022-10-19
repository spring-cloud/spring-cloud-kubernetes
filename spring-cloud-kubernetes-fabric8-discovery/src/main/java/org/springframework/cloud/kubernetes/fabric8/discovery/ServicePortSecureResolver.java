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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;

class ServicePortSecureResolver {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(ServicePortSecureResolver.class));

	private static final Set<String> TRUTHY_STRINGS = Set.of("true", "on", "yes", "1");

	private final KubernetesDiscoveryProperties properties;

	ServicePortSecureResolver(KubernetesDiscoveryProperties properties) {
		this.properties = properties;
	}

	/**
	 * <p>
	 * Returns true if any of the following conditions apply.
	 * <p>
	 * <ul>
	 * <li>service contains a label named 'secured' that is truthy</li>
	 * <li>service contains an annotation named 'secured' that is truthy</li>
	 * <li>the port is one of the known ports used for secure communication</li>
	 * </ul>
	 *
	 */
	boolean resolve(Input input) {

		String securedLabelValue = input.serviceLabels().getOrDefault("secured", "false");
		String serviceName = input.serviceName();
		Integer port = input.port();

		if (TRUTHY_STRINGS.contains(securedLabelValue)) {
			logEntry(serviceName, port, "the service contains a true value for the 'secured' label");
			return true;
		}

		String securedAnnotationValue = input.serviceAnnotations().getOrDefault("secured", "false");
		if (TRUTHY_STRINGS.contains(securedAnnotationValue)) {
			logEntry(serviceName, port, "the service contains a true value for the 'secured' annotation");
			return true;
		}

		if (port != null && properties.knownSecurePorts().contains(port)) {
			logEntry(serviceName, port, "port is known to be a https port");
			return true;
		}

		return false;
	}

	private static void logEntry(String serviceName, Integer port, String part) {
		LOG.debug(() -> "Considering service with name: " + serviceName + " and port " + port + " to be secure since "
				+ part);
	}

	/**
	 * @author wind57
	 */
	record Input(Integer port, String serviceName, Map<String, String> serviceLabels,
			Map<String, String> serviceAnnotations) {

		// used only for testing
		Input(Integer port, String serviceName) {
			this(port, serviceName, null, null);
		}

		Input(Integer port, String serviceName, Map<String, String> serviceLabels,
				Map<String, String> serviceAnnotations) {
			this.port = port;
			this.serviceName = serviceName;
			this.serviceLabels = serviceLabels == null ? Map.of() : serviceLabels;
			this.serviceAnnotations = serviceAnnotations == null ? Map.of() : serviceAnnotations;
		}

	}

}
