/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * TODO break up into delegates if the implementation get's more complicated
 * <p>
 * Returns true if one of the following conditions apply.
 * <p>
 * spring.cloud.kubernetes.discovery.secured has been set to true the service contains a
 * label or an annotation named 'secured' that is truthy the port is one of the known
 * ports used for secure communication
 */
class DefaultIsServicePortSecureResolver {

	private static final Log log = LogFactory.getLog(DefaultIsServicePortSecureResolver.class);

	private static final Set<String> TRUTHY_STRINGS = new HashSet<String>() {
		{
			add("true");
			add("on");
			add("yes");
			add("1");
		}
	};

	private final KubernetesDiscoveryProperties properties;

	DefaultIsServicePortSecureResolver(KubernetesDiscoveryProperties properties) {
		this.properties = properties;
	}

	boolean resolve(Input input) {
		final String securedLabelValue = input.getServiceLabels().getOrDefault("secured", "false");
		if (TRUTHY_STRINGS.contains(securedLabelValue)) {
			if (log.isDebugEnabled()) {
				log.debug("Considering service with name: " + input.getServiceName() + " and port " + input.getPort()
						+ " is secure since the service contains a true value for the 'secured' label");
			}
			return true;
		}

		final String securedAnnotationValue = input.getServiceAnnotations().getOrDefault("secured", "false");
		if (TRUTHY_STRINGS.contains(securedAnnotationValue)) {
			if (log.isDebugEnabled()) {
				log.debug("Considering service with name: " + input.getServiceName() + " and port " + input.getPort()
						+ " is secure since the service contains a true value for the 'secured' annotation");
			}
			return true;
		}

		if (input.getPort() != null && this.properties.getKnownSecurePorts().contains(input.getPort())) {
			if (log.isDebugEnabled()) {
				log.debug("Considering service with name: " + input.getServiceName() + " and port " + input.getPort()
						+ " is secure due to the port being a known https port");
			}
			return true;
		}

		return false;
	}

	static class Input {

		private final Integer port;

		private final String serviceName;

		private final Map<String, String> serviceLabels;

		private final Map<String, String> serviceAnnotations;

		// used only for testing
		Input(Integer port, String serviceName) {
			this(port, serviceName, null, null);
		}

		Input(Integer port, String serviceName, Map<String, String> serviceLabels,
				Map<String, String> serviceAnnotations) {
			this.port = port;
			this.serviceName = serviceName;
			this.serviceLabels = serviceLabels == null ? new HashMap<>() : serviceLabels;
			this.serviceAnnotations = serviceAnnotations == null ? new HashMap<>() : serviceAnnotations;
		}

		public String getServiceName() {
			return this.serviceName;
		}

		public Map<String, String> getServiceLabels() {
			return this.serviceLabels;
		}

		public Map<String, String> getServiceAnnotations() {
			return this.serviceAnnotations;
		}

		public Integer getPort() {
			return this.port;
		}

	}

}
