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

package org.springframework.cloud.kubernetes.fabric8.config.retry;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.kubernetes.fabric8.config.Application;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.secrets.fail-fast=true", "spring.cloud.kubernetes.secrets.retry.enabled=false",
				"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.secrets.name=my-secret",
				"spring.cloud.kubernetes.secrets.enable-api=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.bootstrap.enabled=true" },
		classes = Application.class)
@EnableKubernetesMockClient
class BootstrapSecretsRetryDisabledButConfigRetryEnabled extends SecretsRetryDisabledButConfigRetryEnabled {

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

	@Override
	protected void assertRetryBean(ApplicationContext context) {
		assertThat(context.containsBean("kubernetesSecretsRetryInterceptor")).isTrue();
	}

}
