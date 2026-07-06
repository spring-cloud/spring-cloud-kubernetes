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
import java.util.Set;

/**
 * @author wind57
 */
final class SecretKubernetesSource implements KubernetesSource {

	static final String SECRET_SERVICE_NAMES_ANNOTATION = "spring.cloud.kubernetes.secret.apps";

	static final String SECRET_SERVICE_LABELS_ANNOTATION = "spring.cloud.kubernetes.secret.labels";

	private final Set<String> serviceNames;

	private final Map<String, String> serviceLabels;

	private final String resourceName;

	SecretKubernetesSource(Set<String> serviceNames, Map<String, String> serviceLabels, String resourceName) {
		this.serviceNames = serviceNames;
		this.serviceLabels = serviceLabels;
		this.resourceName = resourceName;
	}

	@Override
	public String resourceName() {
		return resourceName;
	}

	@Override
	public String description() {
		return "secret";
	}

	@Override
	public String requiredResourceLabel() {
		return ConfigurationWatcherConfigurationProperties.SECRET_LABEL;
	}

	@Override
	public Set<String> serviceNames() {
		return serviceNames;
	}

	@Override
	public Map<String, String> serviceLabels() {
		return serviceLabels;
	}

	@Override
	public String toString() {
		return "SecretKubernetesSource{" + "resourceName='" + resourceName + '\'' + ", serviceNames=" + serviceNames
				+ ", serviceLabels=" + serviceLabels + '}';
	}

}
