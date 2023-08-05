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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SECURED;

public final class ServicePortSecureResolver {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(ServicePortSecureResolver.class));

	private static final Set<String> TRUTHY_STRINGS = Set.of("true", "on", "yes", "1");

	private final KubernetesDiscoveryProperties properties;

	public ServicePortSecureResolver(KubernetesDiscoveryProperties properties) {
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
	public boolean resolve(Input input) {

		String serviceName = input.serviceName();
		ServicePortNameAndNumber portData = input.portData();

		Optional<String> securedLabelValue = Optional.ofNullable(input.serviceLabels().get(SECURED));
		if (securedLabelValue.isPresent() && TRUTHY_STRINGS.contains(securedLabelValue.get())) {
			logEntry(serviceName, portData.portNumber(), "the service contains a true value for the 'secured' label");
			return true;
		}

		Optional<String> securedAnnotationValue = Optional.ofNullable(input.serviceAnnotations().get(SECURED));
		if (securedAnnotationValue.isPresent() && TRUTHY_STRINGS.contains(securedAnnotationValue.get())) {
			logEntry(serviceName, portData.portNumber(),
					"the service contains a true value for the 'secured' annotation");
			return true;
		}

		if (properties.knownSecurePorts().contains(portData.portNumber())) {
			logEntry(serviceName, portData.portNumber(), "port is known to be a https port");
			return true;
		}

		if ("https".equalsIgnoreCase(input.portData().portName())) {
			logEntry(serviceName, portData.portNumber(), "port-name is 'https'");
			return true;
		}

		return false;
	}

	private static void logEntry(String serviceName, Integer port, String reason) {
		LOG.debug(() -> "Considering service with name: " + serviceName + " and port " + port + " to be secure since "
				+ reason);
	}

	/**
	 * @author wind57
	 */
	public record Input(ServicePortNameAndNumber portData, String serviceName, Map<String, String> serviceLabels,
			Map<String, String> serviceAnnotations) {

		public Input(ServicePortNameAndNumber portData, String serviceName, Map<String, String> serviceLabels,
				Map<String, String> serviceAnnotations) {
			this.portData = portData;
			this.serviceName = serviceName;
			this.serviceLabels = serviceLabels == null ? Map.of() : serviceLabels;
			this.serviceAnnotations = serviceAnnotations == null ? Map.of() : serviceAnnotations;
		}

	}

}
