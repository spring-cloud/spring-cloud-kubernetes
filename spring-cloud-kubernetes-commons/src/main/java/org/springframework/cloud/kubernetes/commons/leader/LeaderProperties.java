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

package org.springframework.cloud.kubernetes.commons.leader;

import java.time.Duration;

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

	private static final Duration DEFAULT_UPDATE_PERIOD = Duration.ofMillis(60000);

	private static final boolean DEFAULT_PUBLISH_FAILED_EVENTS = false;

	private static final boolean DEFAULT_CREATE_CONFIG_MAP = true;

	/**
	 * Should leader election be enabled. Default: true
	 */
	private boolean enabled = DEFAULT_ENABLED;

	/**
	 * Should leader election be started automatically on startup. Default: true
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
	 * Kubernetes ConfigMap where leaders information will be stored. Default: leaders
	 */
	private String configMapName = DEFAULT_CONFIG_MAP_NAME;

	/**
	 * Leader id property prefix for the ConfigMap. Default: leader.id.
	 */
	private String leaderIdPrefix = DEFAULT_LEADER_ID_PREFIX;

	/**
	 * Leadership status check period. Default: 60s
	 */
	private Duration updatePeriod = DEFAULT_UPDATE_PERIOD;

	/**
	 * Enable/disable publishing events in case leadership acquisition fails. Default:
	 * false
	 */
	private boolean publishFailedEvents = DEFAULT_PUBLISH_FAILED_EVENTS;

	/**
	 * Enable/disable creating ConfigMap if it does not exist. Default: true
	 */
	private boolean createConfigMap = DEFAULT_CREATE_CONFIG_MAP;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public String getRole() {
		return this.role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getNamespace() {
		return this.namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getNamespace(String defaultValue) {
		if (this.namespace == null || this.namespace.isEmpty()) {
			return defaultValue;
		}

		return this.namespace;
	}

	public String getConfigMapName() {
		return this.configMapName;
	}

	public void setConfigMapName(String configMapName) {
		this.configMapName = configMapName;
	}

	public String getLeaderIdPrefix() {
		return this.leaderIdPrefix;
	}

	public void setLeaderIdPrefix(String leaderIdPrefix) {
		this.leaderIdPrefix = leaderIdPrefix;
	}

	public Duration getUpdatePeriod() {
		return this.updatePeriod;
	}

	public void setUpdatePeriod(Duration updatePeriod) {
		this.updatePeriod = updatePeriod;
	}

	public boolean isPublishFailedEvents() {
		return this.publishFailedEvents;
	}

	public void setPublishFailedEvents(boolean publishFailedEvents) {
		this.publishFailedEvents = publishFailedEvents;
	}

	public boolean isCreateConfigMap() {
		return this.createConfigMap;
	}

	public void setCreateConfigMap(boolean createConfigMap) {
		this.createConfigMap = createConfigMap;
	}

}
