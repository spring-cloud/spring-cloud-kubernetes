/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;

@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public class KubernetesDiscoveryProperties extends AutoServiceRegistrationProperties {

	private boolean enabled = true;

	@Value("${spring.application.name:unknown}")
	private String serviceName = "unknown";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getServiceName() {
		return serviceName;
	}

	@Override
	public String toString() {
		return "KubernetesDiscoveryProperties{" +
			"enabled=" + enabled +
			", serviceName='" + serviceName + '\'' +
			'}';
	}
}
