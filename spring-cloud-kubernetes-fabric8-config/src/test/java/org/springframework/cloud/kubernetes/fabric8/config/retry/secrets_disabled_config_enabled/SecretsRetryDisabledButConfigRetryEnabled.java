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

package org.springframework.cloud.kubernetes.fabric8.config.retry.secrets_disabled_config_enabled;

import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.fabric8.config.TestApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.secrets.fail-fast=true", "spring.cloud.kubernetes.secrets.retry.enabled=false",
				"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.secrets.name=my-secret",
				"spring.cloud.kubernetes.secrets.enable-api=true", "spring.main.cloud-platform=KUBERNETES" },
		classes = TestApplication.class)
abstract class SecretsRetryDisabledButConfigRetryEnabled {

	private static final String SECRET_API = "/api/v1/namespaces/default/secrets";

	private static final String CONFIG_MAP_API = "/api/v1/namespaces/default/configmaps";

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	protected SecretsPropertySourceLocator psl;

	protected SecretsPropertySourceLocator verifiablePsl;

	protected static void setup(KubernetesClient mockClient, KubernetesMockServer mockServer) {
		SecretsRetryDisabledButConfigRetryEnabled.mockClient = mockClient;
		SecretsRetryDisabledButConfigRetryEnabled.mockServer = mockServer;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		System.setProperty(Config.KUBERNETES_REQUEST_RETRY_BACKOFFLIMIT_SYSTEM_PROPERTY, "0");
		System.setProperty(Config.KUBERNETES_REQUEST_RETRY_BACKOFFINTERVAL_SYSTEM_PROPERTY, "0");

		// return empty secret list to not fail context creation
		mockServer.expect().withPath(SECRET_API).andReturn(200, new SecretListBuilder().build()).always();
		mockServer.expect().withPath(CONFIG_MAP_API).andReturn(200, new ConfigMapListBuilder().build()).always();
	}

	@Autowired
	private ApplicationContext context;

	@Test
	void locateShouldFailWithoutRetrying() {
		/*
		 * Enabling config retry causes Spring Retry to be enabled and a
		 * RetryOperationsInterceptor bean with NeverRetryPolicy for secrets to be
		 * defined. SecretsPropertySourceLocator should not retry even Spring Retry is
		 * enabled.
		 */
		mockServer.clearExpectations();
		mockServer.expect().withPath(SECRET_API).andReturn(500, "Internal Server Error").once();

		assertRetryBean(context);
		assertThatThrownBy(() -> psl.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("v1/namespaces/default/secrets. Message: Internal Server Error");

		// verify that propertySourceLocator.locate is called only once
		verify(verifiablePsl, times(1)).locate(any());
	}

	protected abstract void assertRetryBean(ApplicationContext context);

}
