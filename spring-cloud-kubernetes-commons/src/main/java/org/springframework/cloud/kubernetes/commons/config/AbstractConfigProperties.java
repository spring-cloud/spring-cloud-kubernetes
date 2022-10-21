/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Abstraction over configuration properties.
 *
 * @author Ioannis Canellos
 * @author Isik Erhan
 */
public abstract class AbstractConfigProperties {

	protected boolean enabled = true;

	protected String name;

	protected String namespace;

	// use config map or secret name to prefix properties
	protected boolean useNameAsPrefix;

	// use profile name to append config map name
	protected boolean includeProfileSpecificSources = true;

	protected boolean failFast = false;

	protected RetryProperties retry = RetryProperties.DEFAULT;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNamespace() {
		return this.namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public boolean isUseNameAsPrefix() {
		return useNameAsPrefix;
	}

	public void setUseNameAsPrefix(boolean useNameAsPrefix) {
		this.useNameAsPrefix = useNameAsPrefix;
	}

	public boolean isIncludeProfileSpecificSources() {
		return includeProfileSpecificSources;
	}

	public void setIncludeProfileSpecificSources(boolean includeProfileSpecificSources) {
		this.includeProfileSpecificSources = includeProfileSpecificSources;
	}

	public boolean isFailFast() {
		return failFast;
	}

	public void setFailFast(boolean failFast) {
		this.failFast = failFast;
	}

	public RetryProperties getRetry() {
		return retry;
	}

	public void setRetry(RetryProperties retry) {
		this.retry = retry;
	}

	/**
	 * Kubernetes config retry properties.
	 * @param initialInterval Initial retry interval in milliseconds.
	 * @param multiplier Maximum interval for backoff.
	 * @param maxInterval Maximum interval
	 * @param maxAttempts Maximum number of attempts.
	 * @param enabled Retry enabled or not
	 */
	public record RetryProperties(@DefaultValue("1000") long initialInterval, @DefaultValue("1.1") double multiplier,
			@DefaultValue("2000") long maxInterval, @DefaultValue("6") int maxAttempts,
			@DefaultValue("true") boolean enabled) {

		/**
		 * Default instance.
		 */
		public static final RetryProperties DEFAULT = new RetryProperties(1000, 1.1, 2000, 6, true);

	}

}
