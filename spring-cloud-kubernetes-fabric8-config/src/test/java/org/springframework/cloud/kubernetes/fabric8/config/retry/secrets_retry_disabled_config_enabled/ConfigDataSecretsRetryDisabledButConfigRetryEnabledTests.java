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

package org.springframework.cloud.kubernetes.fabric8.config.retry.secrets_retry_disabled_config_enabled;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

/**
 * @author Isik Erhan
 */
@TestPropertySource(properties = { "spring.config.import=kubernetes:" })
@EnableKubernetesMockClient
class ConfigDataSecretsRetryDisabledButConfigRetryEnabledTests extends SecretsRetryDisabledButConfigRetryEnabled {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@Autowired
	private Fabric8SecretsPropertySourceLocator secretsPropertySourceLocator;

	@BeforeAll
	static void setup() {
		setup(mockClient, mockServer);
	}

	@BeforeEach
	public void beforeEach() {
		psl = spy(secretsPropertySourceLocator);
		verifiablePsl = psl;
	}

	@Override
	protected void assertRetryBean(ApplicationContext context) {
		assertThat(context.containsBean("configDataSecretsPropertySourceLocator")).isTrue();
		assertThat(context.getBean("configDataSecretsPropertySourceLocator"))
			.isInstanceOf(Fabric8SecretsPropertySourceLocator.class);
	}

}
