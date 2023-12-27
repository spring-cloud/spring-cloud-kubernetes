/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.sanitize_secrets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author wind57
 */
@ConfigurationProperties("sanitize")
class SanitizeProperties {

	private String sanitizeSecretName;

	private String sanitizeSecretNameTwo;

	private String sanitizeConfigMapName;

	public String getSanitizeSecretName() {
		return sanitizeSecretName;
	}

	public void setSanitizeSecretName(String sanitizeSecretName) {
		this.sanitizeSecretName = sanitizeSecretName;
	}

	public String getSanitizeConfigMapName() {
		return sanitizeConfigMapName;
	}

	public void setSanitizeConfigMapName(String sanitizeConfigMapName) {
		this.sanitizeConfigMapName = sanitizeConfigMapName;
	}

	public String getSanitizeSecretNameTwo() {
		return sanitizeSecretNameTwo;
	}

	public void setSanitizeSecretNameTwo(String sanitizeSecretNameTwo) {
		this.sanitizeSecretNameTwo = sanitizeSecretNameTwo;
	}

}
