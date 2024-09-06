/*
 * Copyright 2013-2024 the original author or authors.
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
import org.springframework.util.StringUtils;

/**
 * @author Gytis Trikleris
 */
@ConfigurationProperties("spring.cloud.kubernetes.leader")
public class LeaderProperties {

	/**
	 * Should leader election be enabled. Default: true
	 */
	private boolean enabled = true;

	/**
	 * Should leader election be started automatically on startup. Default: true
	 */
	private boolean autoStartup = true;

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
	private String configMapName = "leaders";

	/**
	 * Leader id property prefix for the ConfigMap. Default: leader.id.
	 */
	private String leaderIdPrefix = "leader.id.";

	/**
	 * Leadership status check period. Default: 60s
	 */
	private Duration updatePeriod = Duration.ofMillis(60000);

	/**
	 * Enable/disable publishing events in case leadership acquisition fails. Default:
	 * false
	 */
	private boolean publishFailedEvents = false;

	/**
	 * Enable/disable creating ConfigMap if it does not exist. Default: true
	 */
	private boolean createConfigMap = true;

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

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getNamespace(String defaultValue) {
		if (!StringUtils.hasText(namespace)) {
			return defaultValue;
		}

		return namespace;
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

	public Duration getUpdatePeriod() {
		return updatePeriod;
	}

	public void setUpdatePeriod(Duration updatePeriod) {
		this.updatePeriod = updatePeriod;
	}

	public boolean isPublishFailedEvents() {
		return publishFailedEvents;
	}

	public void setPublishFailedEvents(boolean publishFailedEvents) {
		this.publishFailedEvents = publishFailedEvents;
	}

	public boolean isCreateConfigMap() {
		return createConfigMap;
	}

	public void setCreateConfigMap(boolean createConfigMap) {
		this.createConfigMap = createConfigMap;
	}

}
