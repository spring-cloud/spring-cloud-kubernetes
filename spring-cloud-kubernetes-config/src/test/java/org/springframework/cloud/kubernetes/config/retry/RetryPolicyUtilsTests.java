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

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Andres Navidad
 */
@RunWith(SpringRunner.class)
public class RetryPolicyUtilsTests {

	@Test
	public void testNormalizeMultipleRetryPolicies() {

		RetryPolicy retryPolicy = new RetryPolicy(1000, -1);
		RetryPolicy normalizedRP = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy);

		assertThat(normalizedRP.getMaxAttempts()).isEqualTo(1000);
		assertThat(normalizedRP.getDelay()).isEqualTo(0);

		RetryPolicy retryPolicy2 = new RetryPolicy(0, -1);
		RetryPolicy normalizedRP2 = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy2);

		assertThat(normalizedRP2.getMaxAttempts()).isEqualTo(1);
		assertThat(normalizedRP2.getDelay()).isEqualTo(0);

		RetryPolicy retryPolicy3 = new RetryPolicy(1, -1);
		RetryPolicy normalizedRP3 = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy3);

		assertThat(normalizedRP3.getMaxAttempts()).isEqualTo(1);
		assertThat(normalizedRP3.getDelay()).isEqualTo(0);

		RetryPolicy retryPolicy4 = new RetryPolicy(-3, -1);
		RetryPolicy normalizedRP4 = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy4);

		assertThat(normalizedRP4.getMaxAttempts()).isEqualTo(-3);
		assertThat(normalizedRP4.getDelay()).isEqualTo(0);

		RetryPolicy retryPolicy5 = new RetryPolicy(-3, -5);
		RetryPolicy normalizedRP5 = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy5);

		assertThat(normalizedRP5.getMaxAttempts()).isEqualTo(-3);
		assertThat(normalizedRP5.getDelay()).isEqualTo(0);

		RetryPolicy retryPolicy6 = new RetryPolicy(-3, 0);
		RetryPolicy normalizedRP6 = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy6);

		assertThat(normalizedRP6.getMaxAttempts()).isEqualTo(-3);
		assertThat(normalizedRP6.getDelay()).isEqualTo(0);

		RetryPolicy retryPolicy7 = new RetryPolicy(-3, 5);
		RetryPolicy normalizedRP7 = RetryPolicyUtils.normalizeRetryPolicy(retryPolicy7);

		assertThat(normalizedRP7.getMaxAttempts()).isEqualTo(-3);
		assertThat(normalizedRP7.getDelay()).isEqualTo(5);

	}

}
