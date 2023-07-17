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

package org.springframework.cloud.kubernetes.commons.config.bootstrap;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.config.App;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = App.class,
		properties = { "spring.cloud.kubernetes.secrets.fail-fast=true",
				"spring.cloud.kubernetes.secrets.retry.enabled=false", "spring.cloud.config.enabled=false" })
class SecretsFailFastEnabledButRetryDisabled {

	@Autowired
	private ConfigurableApplicationContext context;

	@Test
	void shouldNotDefineRetryBeans() {
		assertThat(context.getBeansOfType(RetryOperationsInterceptor.class)).isEmpty();
	}

}
