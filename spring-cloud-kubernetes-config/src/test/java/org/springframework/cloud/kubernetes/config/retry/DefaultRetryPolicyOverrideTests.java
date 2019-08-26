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

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Andres Navidad
 */
@RunWith(SpringRunner.class)
public class DefaultRetryPolicyOverrideTests {

	@Autowired
	private ApplicationContext context;

	@Test
	public void testNotExitsDefaultRetryPolicyBean() {

		RetryPolicyOperations bean = context.getBean(RetryPolicyOperations.class);
		assertThat(bean instanceof DefaultRetryPolicy).isFalse();
	}

	@Configuration
	static class CustomRetryPolicyConfig {

		@Bean
		public RetryPolicyOperations retryPolicyOperationsOverride() {
			return (client, name, namespace) -> new ConfigMap();
		}

	}

}
