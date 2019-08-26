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

import java.util.Optional;

import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Utility class to work with retry policies.
 *
 * @author Andres Navidad
 */
public final class RetryPolicyUtils {

	private RetryPolicyUtils() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static RetryPolicy normalizeRetryPolicy(RetryPolicy retryPolicy) {

		RetryPolicy retryPolicyOpt = Optional.ofNullable(retryPolicy)
				.orElseGet(RetryPolicy::new);

		int maxAttempts = retryPolicyOpt.getMaxAttempts() == 0 ? 1
				: retryPolicyOpt.getMaxAttempts();

		long delay = retryPolicyOpt.getDelay() < 0 ? 0 : retryPolicyOpt.getDelay();

		return new RetryPolicy(maxAttempts, delay);
	}

	public static RetryTemplate getRetryTemplateWithSimpleRetryPolicy(
			RetryPolicy retryPolicy) {

		RetryPolicy normalizedRetryPolicy = RetryPolicyUtils
				.normalizeRetryPolicy(retryPolicy);

		SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
		simpleRetryPolicy.setMaxAttempts(normalizedRetryPolicy.getMaxAttempts());

		FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
		backOffPolicy.setBackOffPeriod(normalizedRetryPolicy.getDelay());

		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(simpleRetryPolicy);
		template.setBackOffPolicy(backOffPolicy);

		return template;
	}

}
