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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;

@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public class KubernetesDiscoveryProperties extends AutoServiceRegistrationProperties {

	private boolean enabled = true;

	@Value("${spring.application.name:unknown}")
	private String serviceName = "unknown";

	/**
	* SpEL expression to filter services
	**/
	private String filter;

	/**
	 * When set, the Kubernetes labels of the services will be included as metadata
	 * of the returned ServiceInstance
	 */
	private boolean enabledAdditionOfLabelsAsMetadata = true;

	/**
	 * When enabledAdditionOfLabelsAsMetadata is set, then the value labelKeysPrefix
	 * will be used as a prefix to the key names in the metadata map
	 */
	private String labelKeysPrefix;


	/**
	 * When set, the Kubernetes annotations of the services will be included as metadata
	 * of the returned ServiceInstance
	 */
	private boolean enabledAdditionOfAnnotationsAsMetadata = true;

	/**
	 * When enabledAdditionOfAnnotationsAsMetadata is set, then the value annotationKeysPrefix
	 * will be used as a prefix to the key names in the metadata map
	 */
	private String annotationKeysPrefix;

	/**
	 * When set, any named Kubernetes service ports will be included as metadata
	 * of the returned ServiceInstance
	 */
	private boolean enabledAdditionOfPortsAsMetadata = true;

	/**
	 * When enabledAdditionOfPortsAsMetadata is set, then the value portKeysPrefix
	 * will be used as a prefix to the key names in the metadata map
	 */
	private String portKeysPrefix = "port.";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getServiceName() {
		return serviceName;
	}

	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter){
		this.filter = filter;
	}

	public boolean isEnabledAdditionOfLabelsAsMetadata() {
		return enabledAdditionOfLabelsAsMetadata;
	}

	public void setEnabledAdditionOfLabelsAsMetadata(boolean enabledAdditionOfLabelsAsMetadata) {
		this.enabledAdditionOfLabelsAsMetadata = enabledAdditionOfLabelsAsMetadata;
	}

	public String getLabelKeysPrefix() {
		return labelKeysPrefix;
	}

	public void setLabelKeysPrefix(String labelKeysPrefix) {
		this.labelKeysPrefix = labelKeysPrefix;
	}

	public boolean isEnabledAdditionOfAnnotationsAsMetadata() {
		return enabledAdditionOfAnnotationsAsMetadata;
	}

	public void setEnabledAdditionOfAnnotationsAsMetadata(
		boolean enabledAdditionOfAnnotationsAsMetadata) {
		this.enabledAdditionOfAnnotationsAsMetadata = enabledAdditionOfAnnotationsAsMetadata;
	}

	public String getAnnotationKeysPrefix() {
		return annotationKeysPrefix;
	}

	public void setAnnotationKeysPrefix(String annotationKeysPrefix) {
		this.annotationKeysPrefix = annotationKeysPrefix;
	}

	public boolean isEnabledAdditionOfPortsAsMetadata() {
		return enabledAdditionOfPortsAsMetadata;
	}

	public void setEnabledAdditionOfPortsAsMetadata(boolean enabledAdditionOfPortsAsMetadata) {
		this.enabledAdditionOfPortsAsMetadata = enabledAdditionOfPortsAsMetadata;
	}

	public String getPortKeysPrefix() {
		return portKeysPrefix;
	}

	public void setPortKeysPrefix(String portKeysPrefix) {
		this.portKeysPrefix = portKeysPrefix;
	}

	@Override
	public String toString() {
		return "KubernetesDiscoveryProperties{" +
			"enabled=" + enabled +
			", serviceName='" + serviceName + '\'' +
			", filter='" + filter + '\'' +
			'}';
	}
}
