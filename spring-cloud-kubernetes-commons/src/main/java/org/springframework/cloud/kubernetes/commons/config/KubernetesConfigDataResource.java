/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.List;
import java.util.Objects;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.boot.context.config.Profiles;
import org.springframework.cloud.kubernetes.commons.KubernetesClientProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.StringUtils;

/**
 * @author Ryan Baxter
 */
public class KubernetesConfigDataResource extends ConfigDataResource {

	private final KubernetesClientProperties properties;

	private final ConfigMapConfigProperties configMapProperties;

	private final SecretsConfigProperties secretsConfigProperties;

	private final boolean optional;

	private final Profiles profiles;

	private Log log;

	private Environment environment;

	public KubernetesConfigDataResource(KubernetesClientProperties properties,
			ConfigMapConfigProperties configMapProperties, SecretsConfigProperties secretsConfigProperties,
			boolean optional, Profiles profiles, Environment environment) {
		this.properties = properties;
		this.configMapProperties = configMapProperties;
		this.secretsConfigProperties = secretsConfigProperties;
		this.optional = optional;
		this.profiles = profiles;
		this.environment = environment;
	}

	public KubernetesClientProperties getProperties() {
		return this.properties;
	}

	public ConfigMapConfigProperties getConfigMapProperties() {
		return configMapProperties;
	}

	public SecretsConfigProperties getSecretsConfigProperties() {
		return secretsConfigProperties;
	}

	public boolean isOptional() {
		return this.optional;
	}

	public String getProfiles() {
		return StringUtils.collectionToCommaDelimitedString(getAcceptedProfiles());
	}

	List<String> getAcceptedProfiles() {
		return this.profiles.getAccepted();
	}

	public void setLog(Log log) {
		this.log = log;
	}

	public Log getLog() {
		return this.log;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		KubernetesConfigDataResource that = (KubernetesConfigDataResource) o;
		return Objects.equals(this.properties, that.properties) && Objects.equals(this.optional, that.optional)
				&& Objects.equals(this.profiles, that.profiles)
				&& Objects.equals(this.configMapProperties, that.configMapProperties)
				&& Objects.equals(this.secretsConfigProperties, that.secretsConfigProperties);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.properties, this.optional, this.profiles, configMapProperties,
				secretsConfigProperties);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("optional", optional).append("profiles", profiles.getAccepted())
				.toString();

	}

}
