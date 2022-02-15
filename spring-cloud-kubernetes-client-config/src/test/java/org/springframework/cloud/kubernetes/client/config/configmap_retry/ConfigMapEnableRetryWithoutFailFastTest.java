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

package org.springframework.cloud.kubernetes.client.config.configmap_retry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientBootstrapConfiguration;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientRetryBootstrapConfiguration;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.RetryConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * @author Ryan Baxter
 */
public class ConfigMapEnableRetryWithoutFailFastTest {

	private static final String API = "/api/v1/namespaces/default/configmaps";

	private static final String SECRETS_API = "/api/v1/namespaces/default/secrets";

	private ConfigurableApplicationContext context;

	private static WireMockServer wireMockServer;

	private static MockedStatic<KubernetesClientUtils> clientUtilsMock;

	@BeforeAll
	public static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor(wireMockServer.port());

		clientUtilsMock = mockStatic(KubernetesClientUtils.class);
		clientUtilsMock.when(KubernetesClientUtils::kubernetesApiClient)
				.thenReturn(new ClientBuilder().setBasePath(wireMockServer.baseUrl()).build());
		stubConfigMapAndSecretsDefaults();
	}

	private static void stubConfigMapAndSecretsDefaults() {
		// return empty config map / secret list to not fail context creation
		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1ConfigMapList()))));
		stubFor(get(SECRETS_API)
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1SecretList()))));
	}

	@AfterAll
	public static void teardown() {
		wireMockServer.stop();
		clientUtilsMock.close();
	}

	protected void setup(String... env) {
		List<String> envList = (env != null) ? new ArrayList<>(Arrays.asList(env)) : new ArrayList<>();
		envList.add("spring.cloud.kubernetes.client.namespace=default");
		String[] envArray = envList.toArray(new String[0]);

		context = new SpringApplicationBuilder(RetryConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
				ConfigReloadAutoConfiguration.class, RefreshAutoConfiguration.class, EndpointAutoConfiguration.class,
				InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
				ConfigurationPropertiesBindingPostProcessor.class,
				ConfigurationPropertiesRebinderAutoConfiguration.class, KubernetesClientBootstrapConfiguration.class,
				KubernetesClientRetryBootstrapConfiguration.class, KubernetesBootstrapConfiguration.class,
				KubernetesClientConfigReloadAutoConfiguration.class)
						.web(org.springframework.boot.WebApplicationType.NONE).properties(envArray).run();
	}

	@AfterEach
	public void afterEach() {
		if (this.context != null) {
			this.context.close();
			this.context = null;
		}
	}

	@Test
	public void doesNotContainRetryableConfigMapPropertySourceLocator() throws Exception {
		stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));
		setup("debug=true", "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.test.enable-retry=true");
		assertThat(context.containsBean("retryableConfigMapPropertySourceLocator")).isFalse();
	}

}
