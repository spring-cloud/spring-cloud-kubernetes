/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.reload_it;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.ClientBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadProperties;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigurationUpdateStrategy;
import org.springframework.cloud.kubernetes.commons.config.reload.PollingSecretsChangeDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.main.allow-bean-definition-overriding=true",
				"logging.level.org.springframework.cloud.kubernetes.commons.config=debug" },
		classes = { PollingReloadSecretTest.TestConfig.class })
@ExtendWith(OutputCaptureExtension.class)
class PollingReloadSecretTest {

	private static WireMockServer wireMockServer;

	private static final boolean FAIL_FAST = false;

	private static final String SECRET_NAME = "mine";

	private static final String NAMESPACE = "spring-k8s";

	private static final boolean[] strategyCalled = new boolean[] { false };

	private static CoreV1Api coreV1Api;

	@BeforeAll
	static void setup() {
		wireMockServer = new WireMockServer(options().dynamicPort());

		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		client.setDebugging(true);
		Configuration.setDefaultApiClient(client);
		coreV1Api = new CoreV1Api();

		String path = "/api/v1/namespaces/spring-k8s/secrets";
		V1Secret secretOne = secret(SECRET_NAME, Map.of());
		V1SecretList listOne = new V1SecretList().addItemsItem(secretOne);

		// needed so that our environment is populated with 'something'
		// this call is done in the method that returns the AbstractEnvironment
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(listOne)))
			.inScenario("my-test")
			.willSetStateTo("go-to-fail"));

		// first reload call fails
		stubFor(get(path).willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
			.inScenario("my-test")
			.whenScenarioStateIs("go-to-fail")
			.willSetStateTo("go-to-ok"));

		V1Secret secretTwo = secret(SECRET_NAME, Map.of("a", "b"));
		V1SecretList listTwo = new V1SecretList().addItemsItem(secretTwo);
		stubFor(get(path).willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(listTwo)))
			.inScenario("my-test")
			.whenScenarioStateIs("go-to-ok"));

	}

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	/**
	 * <pre>
	 *     - we have a PropertySource in the environment
	 *     - first polling cycle tries to read the sources from k8s and fails
	 *     - second polling cycle reads sources from k8s and finds a change
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		// we fail while reading 'secretOne'
		Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1)).until(() -> {
			boolean one = output.getOut().contains("Failure in reading named sources");
			boolean two = output.getOut().contains("Failed to load source");
			boolean three = output.getOut()
				.contains("Reloadable condition was not satisfied, reload will not be triggered");
			boolean updateStrategyNotCalled = !strategyCalled[0];
			return one && two && three && updateStrategyNotCalled;
		});

		// it passes while reading 'secretTwo'
		Awaitility.await()
			.atMost(Duration.ofSeconds(10))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> strategyCalled[0]);
	}

	private static V1Secret secret(String name, Map<String, String> data) {

		Map<String, byte[]> encoded = data.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> Base64.getEncoder().encode(e.getValue().getBytes())));

		return new V1SecretBuilder().withNewMetadata().withName(name).endMetadata().withData(encoded).build();
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		PollingSecretsChangeDetector pollingSecretsChangeDetector(AbstractEnvironment environment,
				ConfigReloadProperties configReloadProperties, ConfigurationUpdateStrategy configurationUpdateStrategy,
				KubernetesClientSecretsPropertySourceLocator kubernetesClientSecretsPropertySourceLocator) {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.initialize();
			return new PollingSecretsChangeDetector(environment, configReloadProperties, configurationUpdateStrategy,
					KubernetesClientSecretsPropertySource.class, kubernetesClientSecretsPropertySourceLocator,
					scheduler);
		}

		@Bean
		@Primary
		AbstractEnvironment environment() {
			MockEnvironment mockEnvironment = new MockEnvironment();
			mockEnvironment.setProperty("spring.cloud.kubernetes.client.namespace", NAMESPACE);

			// simulate that environment already has a
			// KubernetesClientSecretPropertySource,
			// otherwise we can't properly test reload functionality
			SecretsConfigProperties secretsConfigProperties = new SecretsConfigProperties(true, Map.of(), List.of(),
					List.of(), true, SECRET_NAME, NAMESPACE, false, true, false, RetryProperties.DEFAULT);
			KubernetesNamespaceProvider namespaceProvider = new KubernetesNamespaceProvider(mockEnvironment);

			PropertySource<?> propertySource = new KubernetesClientSecretsPropertySourceLocator(coreV1Api,
					namespaceProvider, secretsConfigProperties)
				.locate(mockEnvironment);

			mockEnvironment.getPropertySources().addFirst(propertySource);
			return mockEnvironment;
		}

		@Bean
		@Primary
		ConfigReloadProperties configReloadProperties() {
			return new ConfigReloadProperties(true, false, true, ConfigReloadProperties.ReloadStrategy.REFRESH,
					ConfigReloadProperties.ReloadDetectionMode.POLLING, Duration.ofMillis(2000), Set.of("non-default"),
					false, Duration.ofSeconds(2));
		}

		@Bean
		@Primary
		SecretsConfigProperties secretsConfigProperties() {
			return new SecretsConfigProperties(true, Map.of(), List.of(), List.of(), true, SECRET_NAME, NAMESPACE,
					false, true, FAIL_FAST, RetryProperties.DEFAULT);
		}

		@Bean
		@Primary
		KubernetesNamespaceProvider namespaceProvider(AbstractEnvironment environment) {
			return new KubernetesNamespaceProvider(environment);
		}

		@Bean
		@Primary
		ConfigurationUpdateStrategy configurationUpdateStrategy() {
			return new ConfigurationUpdateStrategy("to-console", () -> {
				strategyCalled[0] = true;
			});
		}

		@Bean
		@Primary
		KubernetesClientSecretsPropertySourceLocator kubernetesClientSecretsPropertySourceLocator(
				SecretsConfigProperties secretsConfigProperties, KubernetesNamespaceProvider namespaceProvider) {
			return new KubernetesClientSecretsPropertySourceLocator(coreV1Api, namespaceProvider,
					secretsConfigProperties);
		}

	}

}
