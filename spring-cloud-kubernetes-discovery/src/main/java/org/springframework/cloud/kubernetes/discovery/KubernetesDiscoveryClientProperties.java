/*
 * Copyright 2013-2021 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ryan Baxter
 */
@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public class KubernetesDiscoveryClientProperties {

	private String discoveryServerUrl;

	private boolean enabled = true;

	/**
	 * If set then only the services and endpoints matching
	 * these namespaces will be fetched from the Kubernetes API server.
	 */
	private List<String> namespaces = new ArrayList<>();

	public String getDiscoveryServerUrl() {
		return discoveryServerUrl;
	}

	public void setDiscoveryServerUrl(String discoveryServerUrl) {
		this.discoveryServerUrl = discoveryServerUrl;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	List<String> getNamespaces() {
		return namespaces;
	}

	void setNamespaces(List<String> namespaces) {
		this.namespaces = namespaces;
	}
}
