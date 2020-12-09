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

package org.springframework.cloud.kubernetes.commons;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/**
 * @author Ryan Baxter
 */
public abstract class AbstractKubernetesInfoContributor implements InfoContributor {

	/**
	 * Kubernetes key.
	 */
	public static final String KUBERNETES = "kubernetes";

	/**
	 * Inside key.
	 */
	public static final String INSIDE = "inside";

	/**
	 * Namespace key.
	 */
	public static final String NAMESPACE = "namespace";

	/**
	 * Pod name key.
	 */
	public static final String POD_NAME = "podName";

	/**
	 * Pod IP key.
	 */
	public static final String POD_IP = "podIp";

	/**
	 * Service account key.
	 */
	public static final String SERVICE_ACCOUNT = "serviceAccount";

	/**
	 * Node name key.
	 */
	public static final String NODE_NAME = "nodeName";

	/**
	 * Host IP key.
	 */
	public static final String HOST_IP = "hostIp";

	/**
	 * Labels key.
	 */
	public static final String LABELS = "labels";

	private static final Log LOG = LogFactory.getLog(AbstractKubernetesInfoContributor.class);

	@Override
	public void contribute(Info.Builder builder) {
		try {
			builder.withDetail(KUBERNETES, getDetails());
		}
		catch (Exception e) {
			LOG.warn("Failed to get pod details", e);
		}
	}

	public abstract Map<String, Object> getDetails();

}
