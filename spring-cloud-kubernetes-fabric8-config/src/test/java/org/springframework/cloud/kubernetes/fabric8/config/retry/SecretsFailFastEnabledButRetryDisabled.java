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

import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
abstract class SecretsFailFastEnabledButRetryDisabled {

	private static final String LIST_API = "/api/v1/namespaces/default/secrets";

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	protected SecretsPropertySourceLocator psl;

	protected SecretsPropertySourceLocator verifiablePsl;

	static void setup(KubernetesClient mockClient, KubernetesMockServer mockServer) {
		SecretsFailFastEnabledButRetryDisabled.mockClient = mockClient;
		SecretsFailFastEnabledButRetryDisabled.mockServer = mockServer;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// return empty secret list to not fail context creation
		mockServer.expect().withPath(LIST_API).andReturn(200, new SecretListBuilder().build()).always();
	}

	@Autowired
	private ApplicationContext context;

	@Test
	void locateShouldFailWithoutRetrying() {
		// clear so that previous mock is disabled
		mockServer.clearExpectations();
		mockServer.expect().withPath(LIST_API).andReturn(500, "Internal Server Error").once();

		assertThat(context.containsBean("kubernetesSecretsRetryInterceptor")).isFalse();
		assertThatThrownBy(() -> psl.locate(new MockEnvironment())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("api/v1/namespaces/default/secrets. Message: Internal Server Error");

		// verify that propertySourceLocator.locate is called only once
		verify(verifiablePsl, times(1)).locate(any());
	}

}
