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

package org.springframework.cloud.kubernetes.fabric8.config.locator_retry;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
abstract class ConfigRetryDisabledButSecretsRetryEnabled {

	private static final String API = "/api/v1/namespaces/default/configmaps/application";

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@Autowired
	private Fabric8ConfigMapPropertySourceLocator propertySourceLocator;

	static void setup(KubernetesClient mockClient, KubernetesMockServer mockServer) {
		ConfigRetryDisabledButSecretsRetryEnabled.mockClient = mockClient;
		ConfigRetryDisabledButSecretsRetryEnabled.mockServer = mockServer;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@Autowired
	private ApplicationContext context;

	@Test
	void locateShouldFailWithoutRetrying() {
		Fabric8ConfigMapPropertySourceLocator psl = spy(propertySourceLocator);
		/*
		 * Enabling secrets retry causes Spring Retry to be enabled and a
		 * RetryOperationsInterceptor bean with NeverRetryPolicy for config maps to be
		 * defined. ConfigMapPropertySourceLocator should not retry even Spring Retry is
		 * enabled.
		 */

		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").once();

		assertRetryBean(context);
		assertThatThrownBy(() -> psl.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read ConfigMap with name 'application' in namespace 'default'");

		// verify that propertySourceLocator.locate is called only once
		verify(psl, times(1)).locate(any());
	}

	protected abstract void assertRetryBean(ApplicationContext context);

}
