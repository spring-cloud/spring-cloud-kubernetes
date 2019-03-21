/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.kubernetes.leader;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Gytis Trikleris
 */
@ConfigurationProperties("spring.cloud.kubernetes.leader")
public class LeaderProperties {

	private static final boolean DEFAULT_ENABLED = true;

	private static final String DEFAULT_LEADER_ID_PREFIX = "leader.id.";

	private static final boolean DEFAULT_AUTO_STARTUP = true;

	private static final String DEFAULT_CONFIG_MAP_NAME = "leaders";

	private static final long DEFAULT_UPDATE_PERIOD = 60000;

	private static final boolean DEFAULT_PUBLISH_FAILED_EVENTS = false;

	/**
	 * Should leader election be enabled.
	 * Default: true
	 */
	private boolean enabled = DEFAULT_ENABLED;

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
	 * Leader id property prefix for the ConfigMap.
	 * Default: leader.id.
	 */
	private String leaderIdPrefix = DEFAULT_LEADER_ID_PREFIX;

	/**
	 * Leadership status check period.
	 * Default: 60s
	 */
	private long updatePeriod = DEFAULT_UPDATE_PERIOD;

	/**
	 * Enable/disable publishing events in case leadership acquisition fails.
	 * Default: false
	 */
	private boolean publishFailedEvents = DEFAULT_PUBLISH_FAILED_EVENTS;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

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

	public String getLeaderIdPrefix() {
		return leaderIdPrefix;
	}

	public void setLeaderIdPrefix(String leaderIdPrefix) {
		this.leaderIdPrefix = leaderIdPrefix;
	}

	public long getUpdatePeriod() {
		return updatePeriod;
	}

	public void setUpdatePeriod(long updatePeriod) {
		this.updatePeriod = updatePeriod;
	}

	public boolean isPublishFailedEvents() {
		return publishFailedEvents;
	}

	public void setPublishFailedEvents(boolean publishFailedEvents) {
		this.publishFailedEvents = publishFailedEvents;
	}
}
