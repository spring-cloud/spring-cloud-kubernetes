/*
 * Copyright 2019-2022 the original author or authors.
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

/**
 * Constants that are to be used across discovery implementations.
 *
 * @author wind57
 */
public final class KubernetesDiscoveryConstants {

	private KubernetesDiscoveryConstants() {

	}

	/**
	 * Primary port label.
	 */
	public static final String PRIMARY_PORT_NAME_LABEL_KEY = "primary-port-name";

	/**
	 * Https scheme.
	 */
	public static final String HTTPS = "https";

	/**
	 * Http scheme.
	 */
	public static final String HTTP = "http";

	/**
	 * Key of the namespace metadata.
	 */
	public static final String NAMESPACE_METADATA_KEY = "k8s_namespace";

	/**
	 * Port name to use when there isn't one set.
	 */
	public static final String UNSET_PORT_NAME = "<unset>";

	/**
	 * Discovery group for Catalog Watch.
	 */
	public static final String DISCOVERY_GROUP = "discovery.k8s.io";

	/**
	 * Discovery version for Catalog Watch.
	 */
	public static final String DISCOVERY_VERSION = "v1";

	/**
	 * Endpoint slice name.
	 */
	public static final String ENDPOINT_SLICE = "EndpointSlice";

	/**
	 * ExternalName type of service.
	 */
	public static final String EXTERNAL_NAME = "ExternalName";

	/**
	 * Type of the service.
	 */
	public static final String SERVICE_TYPE = "type";

	/**
	 * value of the 'secure' label or annotation.
	 */
	public static final String SECURED = "secured";

	/**
	 * catalog watch delay property name.
	 */
	public static final String CATALOG_WATCH_PROPERTY_NAME = "spring.cloud.kubernetes.discovery.catalogServicesWatchDelay";

	/**
	 * default delay for the configuration watcher.
	 */
	public static final String CATALOG_WATCHER_DEFAULT_DELAY = "30000";

	/**
	 * catalog watch delay property name with default value.
	 */
	public static final String CATALOG_WATCH_PROPERTY_WITH_DEFAULT_VALUE = CATALOG_WATCH_PROPERTY_NAME + ":"
			+ CATALOG_WATCHER_DEFAULT_DELAY;

	/**
	 * load balancer port name property.
	 */
	public static final String PORT_NAME_PROPERTY = "'spring.cloud.kubernetes.loadbalancer.portName'";

	/**
	 * message for non-deterministic port.
	 */
	public static final String NON_DETERMINISTIC_PORT_MESSAGE = "Will return 'first' port found, which is non-deterministic";

}
