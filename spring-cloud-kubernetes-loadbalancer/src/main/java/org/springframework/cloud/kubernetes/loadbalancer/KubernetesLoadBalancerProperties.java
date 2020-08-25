/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.loadbalancer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kubernetes load balancer client properties.
 *
 * @author Piotr Minkowski
 */
@ConfigurationProperties(prefix = "spring.cloud.kubernetes.loadbalancer")
public class KubernetesLoadBalancerProperties {

	/**
	 * Load balancer enabled,default true.
	 */
	private Boolean enabled = true;

	/**
	 * {@link KubernetesLoadBalancerMode} setting load balancer server list with ip of pod
	 * or service name. default value is POD.
	 */
	private KubernetesLoadBalancerMode mode = KubernetesLoadBalancerMode.POD;

	/**
	 * cluster domain.
	 */
	private String clusterDomain = "cluster.local";

	/**
	 * service port name.
	 */
	private String portName = "http";

	/**
	 * Get cluster domain.
	 * @return the cluster domain
	 */
	public String getClusterDomain() {
		return clusterDomain;
	}

	/**
	 * Sets cluster domain.
	 * @param clusterDomain the cluster domain
	 */
	public void setClusterDomain(String clusterDomain) {
		this.clusterDomain = clusterDomain;
	}

	/**
	 * Gets mode.
	 * @return the mode
	 */
	public KubernetesLoadBalancerMode getMode() {
		return mode;
	}

	/**
	 * Sets mode.
	 * @param mode the mode
	 */
	public void setMode(KubernetesLoadBalancerMode mode) {
		this.mode = mode;
	}

	/**
	 * Gets enabled.
	 * @return the enabled
	 */
	public Boolean getEnabled() {
		return enabled;
	}

	/**
	 * Sets enabled.
	 * @param enabled the enabled
	 */
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Gets portName.
	 * @return portName port name
	 */
	public String getPortName() {
		return portName;
	}

	/**
	 * Sets portName.
	 * @param portName port name
	 */
	public void setPortName(String portName) {
		this.portName = portName;
	}

}
