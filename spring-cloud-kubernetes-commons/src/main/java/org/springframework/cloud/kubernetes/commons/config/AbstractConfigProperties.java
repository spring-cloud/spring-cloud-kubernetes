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

	// use config map name to prefix properties
	protected boolean useNameAsPrefix;

	// use profile name to append config map name
	protected boolean includeProfileSpecificSources = true;

	protected boolean failFast = false;

	protected RetryProperties retry = new RetryProperties();

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
	 */
	public static class RetryProperties {

		/**
		 * Initial retry interval in milliseconds.
		 */
		private long initialInterval = 1000;

		/**
		 * Multiplier for next interval.
		 */
		private double multiplier = 1.1;

		/**
		 * Maximum interval for backoff.
		 */
		private long maxInterval = 2000;

		/**
		 * Maximum number of attempts.
		 */
		private int maxAttempts = 6;

		private boolean enabled = true;

		public long getInitialInterval() {
			return this.initialInterval;
		}

		public void setInitialInterval(long initialInterval) {
			this.initialInterval = initialInterval;
		}

		public double getMultiplier() {
			return this.multiplier;
		}

		public void setMultiplier(double multiplier) {
			this.multiplier = multiplier;
		}

		public long getMaxInterval() {
			return this.maxInterval;
		}

		public void setMaxInterval(long maxInterval) {
			this.maxInterval = maxInterval;
		}

		public int getMaxAttempts() {
			return this.maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

}
