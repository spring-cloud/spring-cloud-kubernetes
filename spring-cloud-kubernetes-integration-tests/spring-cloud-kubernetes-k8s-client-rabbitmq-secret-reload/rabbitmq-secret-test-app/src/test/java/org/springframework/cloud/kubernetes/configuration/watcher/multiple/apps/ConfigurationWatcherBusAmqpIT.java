/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.configuration.watcher.multiple.apps;

import java.time.Duration;
import java.util.Objects;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
@NativeClientIntegrationTest(withImages = { "rabbitmq-secret-app", "spring-cloud-kubernetes-configuration-watcher" },
		configurationWatcher = @NativeClientIntegrationTest.ConfigurationWatcher(enabled = true,
				watchNamespaces = "default", rabbitMqEnabled = true, refreshDelay = "1"),
		rbacNamespaces = "default", deployRabbitMq = true)
class ConfigurationWatcherBusAmqpIT {

	@BeforeEach
	void setup(NativeClientKubernetesFixture fixture) {
		V1Deployment deployment = fixture.yaml("app/app-deployment.yaml", V1Deployment.class);
		V1Service service = fixture.yaml("app/app-service.yaml", V1Service.class);
		fixture.createAndWait("default", null, deployment, service, true);
	}

	@AfterEach
	void after(NativeClientKubernetesFixture fixture) {
		V1Deployment deployment = fixture.yaml("app/app-deployment.yaml", V1Deployment.class);
		V1Service service = fixture.yaml("app/app-service.yaml", V1Service.class);
		fixture.deleteAndWait("default", deployment, service);
	}

	@Test
	void testRefresh(NativeClientKubernetesFixture fixture) {

		// secret has one label, one that says that we should refresh
		// and one annotation that says that we should refresh some specific services
		V1Secret secret = new V1SecretBuilder().editOrNewMetadata()
			.withName("secret")
			.addToLabels("spring.cloud.kubernetes.secret", "true")
			.addToAnnotations("spring.cloud.kubernetes.secret.apps", "app")
			.endMetadata()
			.build();
		fixture.createAndWait("default", null, secret);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:32321/app").build();

		Boolean[] value = new Boolean[1];
		Awaitilities.awaitUntil(240, 1000, () -> {
			value[0] = serviceClient.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(Boolean.class)
				.retryWhen(retrySpec())
				.block();
			return value[0];
		});

		Assertions.assertThat(value[0]).isTrue();
		fixture.deleteAndWait("default", null, secret);
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(240, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
