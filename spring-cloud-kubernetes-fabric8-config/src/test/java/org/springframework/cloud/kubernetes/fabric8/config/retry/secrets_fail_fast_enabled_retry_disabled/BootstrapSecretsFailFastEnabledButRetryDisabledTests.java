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

package org.springframework.cloud.kubernetes.fabric8.config.retry.secrets_fail_fast_enabled_retry_disabled;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.test.context.TestPropertySource;

/**
 * @author Isik Erhan
 */
@TestPropertySource(properties = { "spring.cloud.bootstrap.enabled=true" })
@EnableKubernetesMockClient
class BootstrapSecretsFailFastEnabledButRetryDisabledTests extends SecretsFailFastEnabledButRetryDisabled {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeAll
	static void setup() {
		setup(mockClient, mockServer);
	}

	@SpyBean
	private Fabric8SecretsPropertySourceLocator propertySourceLocator;

	@BeforeEach
	void beforeEach() {
		psl = propertySourceLocator;
		verifiablePsl = propertySourceLocator;
	}

}
