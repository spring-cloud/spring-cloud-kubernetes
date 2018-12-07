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
import org.springframework.core.style.ToStringCreator;

@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public class KubernetesDiscoveryProperties {

	/** If Kubernetes Discovery is enabled. */
	private boolean enabled = true;

	/** The service name of the local instance. */
	@Value("${spring.application.name:unknown}")
	private String serviceName = "unknown";

	/** SpEL expression to filter services. */
	private String filter;

	private Metadata metadata = new Metadata();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter){
		this.filter = filter;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this)
			.append("enabled", enabled)
			.append("serviceName", serviceName)
			.append("filter", filter)
			.append("metadata", metadata)
			.toString();
	}

	public class Metadata {
		/** When set, the Kubernetes labels of the services will be included as metadata of the returned ServiceInstance. */
		private boolean addLabels = true;

		/** When addLabels is set, then this will be used as a prefix to the key names in the metadata map. */
		private String labelsPrefix;

		/** When set, the Kubernetes annotations of the services will be included as metadata of the returned ServiceInstance. */
		private boolean addAnnotations = true;

		/** When addAnnotations is set, then this will be used as a prefix to the key names in the metadata map. */
		private String annotationsPrefix;

		/** When set, any named Kubernetes service ports will be included as metadata of the returned ServiceInstance. */
		private boolean addPorts = true;

		/** When addPorts is set, then this will be used as a prefix to the key names in the metadata map. */
		private String portsPrefix = "port.";

		public boolean isAddLabels() {
			return addLabels;
		}

		public void setAddLabels(boolean addLabels) {
			this.addLabels = addLabels;
		}

		public String getLabelsPrefix() {
			return labelsPrefix;
		}

		public void setLabelsPrefix(String labelsPrefix) {
			this.labelsPrefix = labelsPrefix;
		}

		public boolean isAddAnnotations() {
			return addAnnotations;
		}

		public void setAddAnnotations(boolean addAnnotations) {
			this.addAnnotations = addAnnotations;
		}

		public String getAnnotationsPrefix() {
			return annotationsPrefix;
		}

		public void setAnnotationsPrefix(String annotationsPrefix) {
			this.annotationsPrefix = annotationsPrefix;
		}

		public boolean isAddPorts() {
			return addPorts;
		}

		public void setAddPorts(boolean addPorts) {
			this.addPorts = addPorts;
		}

		public String getPortsPrefix() {
			return portsPrefix;
		}

		public void setPortsPrefix(String portsPrefix) {
			this.portsPrefix = portsPrefix;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
				.append("addLabels", addLabels)
				.append("labelsPrefix", labelsPrefix)
				.append("addAnnotations", addAnnotations)
				.append("annotationsPrefix", annotationsPrefix)
				.append("addPorts", addPorts)
				.append("portsPrefix", portsPrefix)
				.toString();
		}
	}
}
