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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * we call Fabric8ConfigMapPropertySourceLocator::locate directly, thus no need for
 * bootstrap phase to kick in. As such two flags that might look a bit un-expected:
 * "spring.cloud.kubernetes.config.enabled=false"
 * "spring.cloud.kubernetes.secrets.enabled=false"
 *
 * @author Isik Erhan
 * @author wind57
 */
abstract class ConfigFailFastEnabledButRetryDisabled {

	private static final String API = "/api/v1/namespaces/default/configmaps/application";

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	static void setup(KubernetesClient mockClient, KubernetesMockServer mockServer) {
		ConfigFailFastEnabledButRetryDisabled.mockClient = mockClient;
		ConfigFailFastEnabledButRetryDisabled.mockServer = mockServer;
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@SpyBean
	private Fabric8ConfigMapPropertySourceLocator propertySourceLocator;

	@Autowired
	private ApplicationContext context;

	@Test
	void locateShouldFailWithoutRetrying() {

		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").once();

		assertThat(context.containsBean("kubernetesConfigRetryInterceptor")).isFalse();
		assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read ConfigMap with name 'application' in namespace 'default'");

		// verify that propertySourceLocator.locate is called only once
		verify(propertySourceLocator, times(1)).locate(any());
	}

}
