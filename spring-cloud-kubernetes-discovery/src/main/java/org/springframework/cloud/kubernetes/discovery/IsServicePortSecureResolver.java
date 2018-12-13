/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.springframework.cloud.kubernetes.discovery;

import java.util.HashMap;
import java.util.Map;


/**
 * Will be used to determine where a KubernetesServiceInstance is true or not
 * DefaultIsServicePortSecureResolver is provided as a default if no bean is configured
 */
public interface IsServicePortSecureResolver {

	boolean resolve(Input input);

	class Input {
		private final Integer port;
		private final String serviceName;
		private final Map<String, String> serviceLabels;
		private final Map<String, String> serviceAnnotations;

		//used only for testing
		Input(Integer port, String serviceName) {
			this(port, serviceName, null, null);
		}

		public Input(Integer port, String serviceName,
					 Map<String, String> serviceLabels, Map<String, String> serviceAnnotations) {
			this.port = port;
			this.serviceName = serviceName;
			this.serviceLabels = serviceLabels == null ? new HashMap<>() : serviceLabels;
			this.serviceAnnotations = serviceAnnotations == null ? new HashMap<>() : serviceAnnotations;
		}

		public String getServiceName() {
			return serviceName;
		}

		public Map<String, String> getServiceLabels() {
			return serviceLabels;
		}

		public Map<String, String> getServiceAnnotations() {
			return serviceAnnotations;
		}

		public Integer getPort() {
			return port;
		}
	}
}
