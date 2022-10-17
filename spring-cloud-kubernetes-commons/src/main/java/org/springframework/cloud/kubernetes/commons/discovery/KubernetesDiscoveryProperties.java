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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.style.ToStringCreator;

import static org.springframework.cloud.client.discovery.DiscoveryClient.DEFAULT_ORDER;

@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public class KubernetesDiscoveryProperties {

	/** If Kubernetes Discovery is enabled. */
	private boolean enabled = true;

	/** If discovering all namespaces. */
	private boolean allNamespaces = false;

	/*
	 * If wait for the discovery cache (service and endpoints) to be fully loaded,
	 * otherwise aborts the application on starting.
	 */
	private boolean waitCacheReady = true;

	/**
	 * Timeout for initializing discovery cache, will abort the application if exceeded.
	 **/
	private long cacheLoadingTimeoutSeconds = 60;

	/**
	 * If endpoint addresses not marked 'ready' by the k8s api server should be
	 * discovered.
	 */
	private boolean includeNotReadyAddresses = false;

	/**
	 * SpEL expression to filter services AFTER they have been retrieved from the
	 * Kubernetes API server.
	 */
	private String filter;

	/** Set the port numbers that are considered secure and use HTTPS. */
	private Set<Integer> knownSecurePorts = Stream.of(443, 8443).collect(Collectors.toCollection(HashSet::new));

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

	private int order = DEFAULT_ORDER;

	/**
	 * If set and allNamespaces is false, then only the services and endpoints matching
	 * these namespaces will be fetched from the Kubernetes API server.
	 */
	private List<String> namespaces = new ArrayList<>();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
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

	public boolean isIncludeNotReadyAddresses() {
		return includeNotReadyAddresses;
	}

	public void setIncludeNotReadyAddresses(boolean includeNotReadyAddresses) {
		this.includeNotReadyAddresses = includeNotReadyAddresses;
	}

	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public List<String> getNamespaces() {
		return namespaces;
	}

	public void setNamespaces(List<String> namespaces) {
		this.namespaces = namespaces;
	}

	public boolean isWaitCacheReady() {
		return waitCacheReady;
	}

	public void setWaitCacheReady(boolean waitCacheReady) {
		this.waitCacheReady = waitCacheReady;
	}

	public long getCacheLoadingTimeoutSeconds() {
		return cacheLoadingTimeoutSeconds;
	}

	public void setCacheLoadingTimeoutSeconds(long cacheLoadingTimeoutSeconds) {
		this.cacheLoadingTimeoutSeconds = cacheLoadingTimeoutSeconds;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("enabled", this.enabled).append("filter", this.filter)
				.append("knownSecurePorts", this.knownSecurePorts).append("serviceLabels", this.serviceLabels)
				.append("metadata", this.metadata).toString();
	}

	/**
	 * @param addLabels include labels as metadata
	 * @param labelsPrefix prefix for the labels
	 * @param addAnnotations include annotations as metadata
	 * @param annotationsPrefix prefix for the annotations
	 * @param addPorts include ports as metadata
	 * @param portsPrefix prefix for the ports, by default it is "port."
	 */
	public record Metadata(@DefaultValue("true") boolean addLabels, String labelsPrefix,
			@DefaultValue("true") boolean addAnnotations, String annotationsPrefix,
			@DefaultValue("true") boolean addPorts, @DefaultValue("port.") String portsPrefix) {

		@ConstructorBinding
		public Metadata {

		}

		// needed in order to get the defaults for some fields
		public Metadata() {
			this(true, null, true, null, true, "port.");
		}

	}

}
