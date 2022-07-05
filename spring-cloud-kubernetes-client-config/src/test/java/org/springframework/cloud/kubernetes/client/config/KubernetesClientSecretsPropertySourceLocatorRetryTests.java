/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.config.reload.KubernetesClientConfigReloadAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.retry.annotation.RetryConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Isik Erhan
 */
public class KubernetesClientSecretsPropertySourceLocatorRetryTests {

	private static final String API = "/api/v1/namespaces/default/secrets";

	private static final String CONFIG_MAPS_API = "/api/v1/namespaces/default/configmaps";

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
		stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1SecretList()))));
		stubFor(get(CONFIG_MAPS_API)
				.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(new V1ConfigMapList()))));
	}

	@AfterAll
	public static void teardown() {
		wireMockServer.stop();
		clientUtilsMock.close();
	}

	@AfterEach
	public void afterEach() {
		WireMock.reset();
		stubConfigMapAndSecretsDefaults();
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
			"spring.cloud.kubernetes.client.namespace=default", "spring.cloud.kubernetes.secrets.fail-fast=true",
			"spring.cloud.kubernetes.secrets.retry.max-attempts=5", "spring.cloud.kubernetes.secrets.name=my-secret",
			"spring.cloud.kubernetes.secrets.enable-api=true" },
			classes = App.class)
	class SecretsRetryEnabled {

		@SpyBean
		private KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

		@Test
		public void locateShouldNotRetryWhenThereIsNoFailure() {

			Map<String, byte[]> data = new HashMap<>();
			data.put("some.sensitive.prop", "theSensitiveValue".getBytes());
			data.put("some.sensitive.number", "1".getBytes());

			V1SecretList secretList = new V1SecretList()
					.addItemsItem(new V1Secret().metadata(new V1ObjectMeta().name("my-secret")).data(data));

			stubFor(get(API).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

			PropertySource<?> propertySource = Assertions
					.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

			// verify locate is called only once
			verify(propertySourceLocator, times(1)).locate(any());

			// validate the contents of the property source
			assertThat(propertySource.getProperty("some.sensitive.prop")).isEqualTo("theSensitiveValue");
			assertThat(propertySource.getProperty("some.sensitive.number")).isEqualTo("1");
		}

		@Test
		public void locateShouldRetryAndRecover() {
			Map<String, byte[]> data = new HashMap<>();
			data.put("some.sensitive.prop", "theSensitiveValue".getBytes());
			data.put("some.sensitive.number", "1".getBytes());

			V1SecretList secretList = new V1SecretList()
					.addItemsItem(new V1Secret().metadata(new V1ObjectMeta().name("my-secret")).data(data));

			// fail 3 times
			stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs(STARTED)
					.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed once"));

			stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed once")
					.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed twice"));

			stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed twice")
					.willReturn(aResponse().withStatus(500)).willSetStateTo("Failed thrice"));

			// then succeed
			stubFor(get(API).inScenario("Retry and Recover").whenScenarioStateIs("Failed thrice")
					.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(secretList))));

			PropertySource<?> propertySource = Assertions
					.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

			// verify retried 4 times
			verify(propertySourceLocator, times(4)).locate(any());

			// validate the contents of the property source
			assertThat(propertySource.getProperty("some.sensitive.prop")).isEqualTo("theSensitiveValue");
			assertThat(propertySource.getProperty("some.sensitive.number")).isEqualTo("1");
		}

		@Test
		public void locateShouldRetryAndFail() {
			// fail all the 5 requests
			stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

			assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("Unable to read Secret with name 'my-secret' or labels [{}] in namespace 'default'");

			// verify retried 5 times until failure
			verify(propertySourceLocator, times(5)).locate(any());
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
			properties = { "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.kubernetes.secrets.name=my-secret", "spring.cloud.kubernetes.secrets.enable-api=true" },
			classes = App.class)
	class SecretsFailFastDisabled {

		@SpyBean
		private KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

		@Test
		public void locateShouldNotRetry() {

			stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

			Assertions.assertDoesNotThrow(() -> propertySourceLocator.locate(new MockEnvironment()));

			// verify locate is called only once
			verify(propertySourceLocator, times(1)).locate(any());
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
			properties = { "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.kubernetes.secrets.fail-fast=true",
					"spring.cloud.kubernetes.secrets.retry.enabled=false",
					"spring.cloud.kubernetes.config.fail-fast=true", "spring.cloud.kubernetes.secrets.name=my-secret",
					"spring.cloud.kubernetes.secrets.enable-api=true" },
			classes = App.class)
	class SecretsRetryDisabledButConfigRetryEnabled {

		@SpyBean
		private KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

		@Autowired
		private ApplicationContext context;

		@Test
		public void locateShouldFailWithoutRetrying() {

			/*
			 * Enabling config retry causes Spring Retry to be enabled and a
			 * RetryOperationsInterceptor bean with NeverRetryPolicy for secrets to be
			 * defined. SecretsPropertySourceLocator should not retry even Spring Retry is
			 * enabled.
			 */

			stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

			assertThat(context.containsBean("kubernetesSecretsRetryInterceptor")).isTrue();
			assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("Unable to read Secret with name 'my-secret' or labels [{}] in namespace 'default'");

			// verify that propertySourceLocator.locate is called only once
			verify(propertySourceLocator, times(1)).locate(any());
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
			"spring.cloud.kubernetes.client.namespace=default", "spring.cloud.kubernetes.secrets.fail-fast=true",
			"spring.cloud.kubernetes.secrets.retry.enabled=false", "spring.cloud.kubernetes.secrets.name=my-secret",
			"spring.cloud.kubernetes.secrets.enable-api=true" },
			classes = App.class)
	class SecretsFailFastEnabledButRetryDisabled {

		@SpyBean
		private KubernetesClientSecretsPropertySourceLocator propertySourceLocator;

		@Autowired
		private ApplicationContext context;

		@Test
		public void locateShouldFailWithoutRetrying() {

			stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

			assertThat(context.containsBean("kubernetesSecretsRetryInterceptor")).isFalse();
			assertThatThrownBy(() -> propertySourceLocator.locate(new MockEnvironment()))
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("Unable to read Secret with name 'my-secret' or labels [{}] in namespace 'default'");

			// verify that propertySourceLocator.locate is called only once
			verify(propertySourceLocator, times(1)).locate(any());
		}

	}

	@Nested
	public class EnableRetryWithoutFailFastTest {

		private ConfigurableApplicationContext context;

		protected void setup(String... env) {
			List<String> envList = (env != null) ? new ArrayList<>(Arrays.asList(env)) : new ArrayList<>();
			envList.add("spring.cloud.kubernetes.client.namespace=default");
			String[] envArray = envList.toArray(new String[0]);

			context = new SpringApplicationBuilder(RetryConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
					ConfigReloadAutoConfiguration.class, RefreshAutoConfiguration.class,
					EndpointAutoConfiguration.class, InfoEndpointAutoConfiguration.class,
					RefreshEndpointAutoConfiguration.class, ConfigurationPropertiesBindingPostProcessor.class,
					ConfigurationPropertiesRebinderAutoConfiguration.class,
					KubernetesClientBootstrapConfiguration.class, KubernetesClientRetryBootstrapConfiguration.class,
					KubernetesBootstrapConfiguration.class, KubernetesClientConfigReloadAutoConfiguration.class)
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
		public void doesNotContainRetryableSecretsPropertySourceLocator() throws Exception {
			stubFor(get(API).willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));
			setup("debug=true",
					"spring.cloud.kubernetes.test.enable-retry=true", "spring.cloud.kubernetes.secrets.name=my-secret",
					"spring.cloud.kubernetes.secrets.enable-api=true");
			assertThat(context.containsBean("retryableSecretsPropertySourceLocator")).isFalse();
		}

	}

	@SpringBootApplication
	static class App {

	}

}
