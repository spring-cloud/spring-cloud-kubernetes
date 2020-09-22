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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.style.ToStringCreator;

/**
 * Kubernetes discovery properties.
 *
 * @author Ioannis Canellos
 */
@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public class KubernetesDiscoveryProperties {

	/** If Kubernetes Discovery is enabled. */
	private boolean enabled = true;

	/** The service name of the local instance. */
	@Value("${spring.application.name:unknown}")
	private String serviceName = "unknown";

	/** If discovering all namespaces. */
	private boolean allNamespaces = false;

	/**
	 * SpEL expression to filter services AFTER they have been retrieved from the
	 * Kubernetes API server.
	 */
	private String filter;

	/** Set the port numbers that are considered secure and use HTTPS. */
	private Set<Integer> knownSecurePorts = new HashSet<Integer>() {
		{
			add(443);
			add(8443);
		}
	};

	/**
	 * If set, then only the services matching these labels will be fetched from the
	 * Kubernetes API server.
	 */
	private Map<String, String> serviceLabels = new HashMap<>();

	/**
	 * If set then the port with a given name is used as primary when multiple ports are
	 * defined for a service.
	 */
	private String primaryPortName;

	private Metadata metadata = new Metadata();

	private int order = DiscoveryClient.DEFAULT_ORDER;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getFilter() {
		return this.filter;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public Set<Integer> getKnownSecurePorts() {
		return this.knownSecurePorts;
	}

	public void setKnownSecurePorts(Set<Integer> knownSecurePorts) {
		this.knownSecurePorts = knownSecurePorts;
	}

	public Map<String, String> getServiceLabels() {
		return this.serviceLabels;
	}

	public void setServiceLabels(Map<String, String> serviceLabels) {
		this.serviceLabels = serviceLabels;
	}

	public String getPrimaryPortName() {
		return primaryPortName;
	}

	public void setPrimaryPortName(String primaryPortName) {
		this.primaryPortName = primaryPortName;
	}

	public Metadata getMetadata() {
		return this.metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public boolean isAllNamespaces() {
		return allNamespaces;
	}

	public void setAllNamespaces(boolean allNamespaces) {
		this.allNamespaces = allNamespaces;
	}

	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("enabled", this.enabled).append("serviceName", this.serviceName)
				.append("filter", this.filter).append("knownSecurePorts", this.knownSecurePorts)
				.append("serviceLabels", this.serviceLabels).append("metadata", this.metadata).toString();
	}

	/**
	 * Metadata properties.
	 */
	public class Metadata {

		/**
		 * When set, the Kubernetes labels of the services will be included as metadata of
		 * the returned ServiceInstance.
		 */
		private boolean addLabels = true;

		/**
		 * When addLabels is set, then this will be used as a prefix to the key names in
		 * the metadata map.
		 */
		private String labelsPrefix;

		/**
		 * When set, the Kubernetes annotations of the services will be included as
		 * metadata of the returned ServiceInstance.
		 */
		private boolean addAnnotations = true;

		/**
		 * When addAnnotations is set, then this will be used as a prefix to the key names
		 * in the metadata map.
		 */
		private String annotationsPrefix;

		/**
		 * When set, any named Kubernetes service ports will be included as metadata of
		 * the returned ServiceInstance.
		 */
		private boolean addPorts = true;

		/**
		 * When addPorts is set, then this will be used as a prefix to the key names in
		 * the metadata map.
		 */
		private String portsPrefix = "port.";

		public boolean isAddLabels() {
			return this.addLabels;
		}

		public void setAddLabels(boolean addLabels) {
			this.addLabels = addLabels;
		}

		public String getLabelsPrefix() {
			return this.labelsPrefix;
		}

		public void setLabelsPrefix(String labelsPrefix) {
			this.labelsPrefix = labelsPrefix;
		}

		public boolean isAddAnnotations() {
			return this.addAnnotations;
		}

		public void setAddAnnotations(boolean addAnnotations) {
			this.addAnnotations = addAnnotations;
		}

		public String getAnnotationsPrefix() {
			return this.annotationsPrefix;
		}

		public void setAnnotationsPrefix(String annotationsPrefix) {
			this.annotationsPrefix = annotationsPrefix;
		}

		public boolean isAddPorts() {
			return this.addPorts;
		}

		public void setAddPorts(boolean addPorts) {
			this.addPorts = addPorts;
		}

		public String getPortsPrefix() {
			return this.portsPrefix;
		}

		public void setPortsPrefix(String portsPrefix) {
			this.portsPrefix = portsPrefix;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("addLabels", this.addLabels)
					.append("labelsPrefix", this.labelsPrefix).append("addAnnotations", this.addAnnotations)
					.append("annotationsPrefix", this.annotationsPrefix).append("addPorts", this.addPorts)
					.append("portsPrefix", this.portsPrefix).toString();
		}

	}

}
