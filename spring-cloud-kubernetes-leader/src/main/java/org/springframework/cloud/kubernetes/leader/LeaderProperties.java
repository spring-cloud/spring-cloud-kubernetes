/*
 * Copyright (C) 2018 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.leader;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@ConfigurationProperties("spring.cloud.kubernetes.leader")
public class LeaderProperties {

	private static final String DEFAULT_LEADER_ID_PREFIX = "leader.id.";

	private static final boolean DEFAULT_AUTO_STARTUP = true;

	private static final String DEFAULT_CONFIG_MAP_NAME = "leaders";

	private static final long DEFAULT_LEASE_DURATION = 30000;

	private static final long DEFAULT_RETRY_PERIOD = 5000;

	private static final double DEFAULT_JITTER_FACTOR = 1.2;

	/**
	 * Should leader election be started automatically on startup.
	 * Default: true
	 */
	private boolean autoStartup = DEFAULT_AUTO_STARTUP;

	/**
	 * Role for which leadership this candidate will compete.
	 */
	private String role;

	/**
	 * Kubernetes namespace where the leaders ConfigMap and candidates are located.
	 */
	private String namespace;

	/**
	 * Kubernetes ConfigMap where leaders information will be stored.
	 * Default: leaders
	 */
	private String configMapName = DEFAULT_CONFIG_MAP_NAME;

	/**
	 * Kubernetes labels common to all leadership candidates.
	 * Default: empty
	 */
	private Map<String, String> labels = Collections.emptyMap();

	/**
	 * Leader id property prefix for the ConfigMap.
	 * Default: leader.id.
	 */
	private String leaderIdPrefix = DEFAULT_LEADER_ID_PREFIX;

	/**
	 * Time period after which leader should check it's leadership or new leader should be elected.
	 * Default: 30s
	 */
	private long leaseDuration = DEFAULT_LEASE_DURATION;

	/**
	 * Time period after connections should be retired after failure.
	 * Default: 5s
	 */
	private long retryPeriod = DEFAULT_RETRY_PERIOD;

	/**
	 * A parameter to randomise scheduler.
	 * Default: 1.2
	 */
	private double jitterFactor = DEFAULT_JITTER_FACTOR;

	/**
	 * Enable/disable publishing events in case leadership acquisition fails.
	 * Default: false
	 */
	private boolean publishFailedEvents = false;

	public boolean isAutoStartup() {
		return autoStartup;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getNamespace() {
		return namespace;
	}

	public String getNamespace(String defaultValue) {
		if (namespace == null || namespace.isEmpty()) {
			return defaultValue;
		}

		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getConfigMapName() {
		return configMapName;
	}

	public void setConfigMapName(String configMapName) {
		this.configMapName = configMapName;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = Collections.unmodifiableMap(labels);
	}

	public String getLeaderIdPrefix() {
		return leaderIdPrefix;
	}

	public void setLeaderIdPrefix(String leaderIdPrefix) {
		this.leaderIdPrefix = leaderIdPrefix;
	}

	public long getLeaseDuration() {
		return leaseDuration;
	}

	public void setLeaseDuration(long leaseDuration) {
		this.leaseDuration = leaseDuration;
	}

	public long getRetryPeriod() {
		return retryPeriod;
	}

	public void setRetryPeriod(long retryPeriod) {
		this.retryPeriod = retryPeriod;
	}

	public double getJitterFactor() {
		return jitterFactor;
	}

	public void setJitterFactor(double jitterFactor) {
		this.jitterFactor = jitterFactor;
	}

	public boolean isPublishFailedEvents() {
		return publishFailedEvents;
	}

	public void setPublishFailedEvents(boolean publishFailedEvents) {
		this.publishFailedEvents = publishFailedEvents;
	}
}
