/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
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

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
public class LeaderConfiguration {

	private static final String DEFAULT_LEADER_ID_PREFIX = "leader.id.";

	private static final boolean DEFAULT_AUTO_STARTUP = true;

	private static final String DEFAULT_CONFIG_MAP_NAME = "leaders";

	private static final long DEFAULT_LEASE_DURATION = 30000;

	private static final long DEFAULT_RETRY_PERIOD = 5000;

	private static final double DEFAULT_JITTER_FACTOR = 1.2;

	private boolean autoStartup = DEFAULT_AUTO_STARTUP;

	private String namespace;

	private String configMapName = DEFAULT_CONFIG_MAP_NAME;

	private Map<String, String> labels = Collections.emptyMap();

	private String leaderIdPrefix = DEFAULT_LEADER_ID_PREFIX;

	private long leaseDuration = DEFAULT_LEASE_DURATION;

	private long retryPeriod = DEFAULT_RETRY_PERIOD;

	private double jitterFactor = DEFAULT_JITTER_FACTOR;

	public boolean isAutoStartup() {
		return autoStartup;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
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
}
