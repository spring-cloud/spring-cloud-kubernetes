/*
 * Copyright 2013-present the original author or authors.
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

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;

/**
 * @author Ryan Baxter
 */
public abstract class AbstractKubernetesHealthIndicator extends AbstractHealthIndicator {

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

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		try {
			builder.up().withDetails(getDetails());
		}
		catch (Exception e) {
			builder.down(e);
		}
	}

	protected abstract Map<String, Object> getDetails() throws Exception;

}
