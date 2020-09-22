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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

/**
 * @author Ryan Baxter
 */
@ConfigurationProperties("spring.cloud.kubernetes.configuration.watcher")
public class ConfigurationWatcherConfigurationProperties {

	/**
	 * Amount of time to delay the posting of the event to allow the app volume to update
	 * data.
	 */
	@DurationUnit(ChronoUnit.MILLIS)
	private Duration refreshDelay = Duration.ofMillis(120000);

	private int threadPoolSize = 1;

	private String configLabel = "spring.cloud.kubernetes.config";

	private String secretLabel = "spring.cloud.kubernetes.secret";

	private String actuatorPath = "/actuator";

	private Integer actuatorPort = -1;

	public String getActuatorPath() {
		return actuatorPath;
	}

	public void setActuatorPath(String actuatorPath) {
		String normalizedPath = actuatorPath;
		if (!normalizedPath.startsWith("/")) {
			normalizedPath = "/" + normalizedPath;
		}
		if (normalizedPath.endsWith("/")) {
			normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
		}
		this.actuatorPath = normalizedPath;
	}

	public Integer getActuatorPort() {
		return actuatorPort;
	}

	public void setActuatorPort(Integer actuatorPort) {
		this.actuatorPort = actuatorPort;
	}

	public String getSecretLabel() {
		return secretLabel;
	}

	public void setSecretLabel(String secretLabel) {
		this.secretLabel = secretLabel;
	}

	public String getConfigLabel() {
		return configLabel;
	}

	public void setConfigLabel(String configLabel) {
		this.configLabel = configLabel;
	}

	public Duration getRefreshDelay() {
		return refreshDelay;
	}

	public void setRefreshDelay(Duration refreshDelay) {
		this.refreshDelay = refreshDelay;
	}

	public int getThreadPoolSize() {
		return threadPoolSize;
	}

	public void setThreadPoolSize(int threadPoolSize) {
		this.threadPoolSize = threadPoolSize;
	}

}
