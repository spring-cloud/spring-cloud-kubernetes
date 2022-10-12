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

package org.springframework.cloud.kubernetes.fabric8.config.locator_retry.config_retry_enabled;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigDataRetryableConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.Application;

import static org.mockito.Mockito.spy;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.secrets.enabled=false",
				"spring.cloud.kubernetes.client.namespace=default", "spring.cloud.kubernetes.config.fail-fast=true",
				"spring.cloud.kubernetes.config.retry.max-attempts=5", "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:" },
		classes = Application.class)
@EnableKubernetesMockClient
class ConfigDataConfigRetryEnabled extends ConfigRetryEnabled {

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@MockBean
	private KubernetesNamespaceProvider namespaceProvider;

	@BeforeAll
	static void setup() {
		setup(mockClient, mockServer);
	}

	@Autowired
	ConfigDataRetryableConfigMapPropertySourceLocator propertySourceLocator;

	@BeforeEach
	public void beforeEach() {
		psl = propertySourceLocator;
		verifiablePsl = spy(propertySourceLocator.getConfigMapPropertySourceLocator());
		propertySourceLocator.setConfigMapPropertySourceLocator(verifiablePsl);
	}

}
