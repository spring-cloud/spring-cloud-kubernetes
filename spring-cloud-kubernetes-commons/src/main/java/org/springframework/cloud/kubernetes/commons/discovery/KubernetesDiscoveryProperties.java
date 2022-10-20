/*
 * Copyright 2013-2022 the original author or authors.
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

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static org.springframework.cloud.client.discovery.DiscoveryClient.DEFAULT_ORDER;

/**
 * @param enabled if kubernetes discovery is enabled
 * @param allNamespaces if discover is enabled for all namespaces
 * @param waitCacheReady wait for the discovery cache (service and endpoints) to be fully
 * loaded, otherwise aborts the application on starting
 * @param cacheLoadingTimeoutSeconds timeout for initializing discovery cache, will abort
 * the application if exceeded.
 * @param includeNotReadyAddresses include as discovered if endpoint addresses is not
 * marked with 'ready' by kubernetes
 * @param filter SpEL expression to filter services after they have been retrieved from
 * the Kubernetes API server.
 * @param knownSecurePorts set of known secure ports
 * @param serviceLabels if set, then only the services matching these labels will be
 * fetched from the Kubernetes API server.
 * @param primaryPortName If set then the port with a given name is used as primary when
 * multiple ports are defined for a service.
 */
// @formatter:off
@ConfigurationProperties("spring.cloud.kubernetes.discovery")
public record KubernetesDiscoveryProperties(
		@DefaultValue("true") boolean enabled, boolean allNamespaces,
		@DefaultValue("true") boolean waitCacheReady,
		@DefaultValue("60") long cacheLoadingTimeoutSeconds,
		boolean includeNotReadyAddresses, String filter,
		@DefaultValue({"443", "8443"}) Set<Integer> knownSecurePorts,
		@DefaultValue Map<String, String> serviceLabels, String primaryPortName,
		@DefaultValue Metadata metadata,
		@DefaultValue("" + DEFAULT_ORDER) int order) {
// @formatter:on

	/**
	 * Default instance.
	 */
	public static final KubernetesDiscoveryProperties DEFAULT = new KubernetesDiscoveryProperties(true, false, true, 60,
			false, null, Set.of(), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0);

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

		/**
		 * Default instance.
		 */
		public static final Metadata DEFAULT = new Metadata(true, null, true, null, true, "port.");

	}

}
