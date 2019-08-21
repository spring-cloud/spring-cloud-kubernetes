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

package org.springframework.cloud.kubernetes.config.retry;

/**
 * Class to wrap some properties to define a retry policy.
 *
 * @author Andres Navidad
 */
public class RetryPolicy {

	private int maxAttempts = 1;

	private long delay = 1000;

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public RetryPolicy() {
	}

	public RetryPolicy(int maxAttempts, long delay) {
		this.maxAttempts = maxAttempts;
		this.delay = delay;
	}

	@Override
	public String toString() {
		return new StringBuilder("RetryPolicy {").append("maxAttempts=")
				.append(maxAttempts).append(", delay=").append(delay).append('}')
				.toString();

	}

}
