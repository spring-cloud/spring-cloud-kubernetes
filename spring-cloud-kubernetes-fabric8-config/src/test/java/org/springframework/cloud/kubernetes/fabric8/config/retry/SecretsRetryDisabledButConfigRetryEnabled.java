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
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.kubernetes.fabric8.config.Application;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySourceLocator;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.cloud.kubernetes.client.namespace=default",
				"spring.cloud.kubernetes.secrets.fail-fast=true", "spring.cloud.kubernetes.secrets.retry.enabled=false",
				"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.secrets.name=my-secret",
				"spring.cloud.kubernetes.secrets.enable-api=true", "spring.main.cloud-platform=KUBERNETES" },
		classes = Application.class)
@EnableKubernetesMockClient
class SecretsRetryDisabledButConfigRetryEnabled {

	private static final String API = "/api/v1/namespaces/default/secrets/my-secret";

	private static final String LIST_API = "/api/v1/namespaces/default/secrets";

	private static KubernetesMockServer mockServer;

	private static KubernetesClient mockClient;

	@BeforeAll
	static void setup() {
		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// return empty secret list to not fail context creation
		mockServer.expect().withPath(LIST_API).andReturn(200, new SecretListBuilder().build()).always();
	}

	@SpyBean
	private Fabric8SecretsPropertySourceLocator propertySourceLocator;

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

		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").once();

		assertThat(context.containsBean("kubernetesSecretsRetryInterceptor")).isTrue();
		assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to read Secret with name 'my-secret' in namespace 'default'");

		// verify that propertySourceLocator.locate is called only once
		verify(propertySourceLocator, times(1)).locate(any());
	}

}
