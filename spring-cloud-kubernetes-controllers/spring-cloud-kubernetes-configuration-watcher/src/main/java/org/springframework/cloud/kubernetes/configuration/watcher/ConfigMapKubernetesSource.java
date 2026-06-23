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

final class ConfigMapKubernetesSource implements KubernetesSource {

	static final String CONFIGMAP_SERVICE_NAMES_ANNOTATION = "spring.cloud.kubernetes.configmap.apps";

	static final String CONFIGMAP_SERVICE_LABELS_ANNOTATION = "spring.cloud.kubernetes.configmap.labels";

	private final Set<String> serviceNames;

	private final Map<String, String> serviceLabels;

	ConfigMapKubernetesSource(Set<String> serviceNames, Map<String, String> serviceLabels) {
		this.serviceNames = serviceNames;
		this.serviceLabels = serviceLabels;
	}

	@Override
	public String description() {
		return "configmap";
	}

	@Override
	public String serviceNamesAnnotation() {
		return CONFIGMAP_SERVICE_NAMES_ANNOTATION;
	}

	@Override
	public String serviceLabelsAnnotation() {
		return CONFIGMAP_SERVICE_LABELS_ANNOTATION;
	}

	@Override
	public Set<String> serviceNames() {
		return serviceNames;
	}

	@Override
	public Map<String, String> serviceLabels() {
		return serviceLabels;
	}
}
