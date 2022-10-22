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

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 *
 * @author wind57 Kubernetes config retry properties.
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
