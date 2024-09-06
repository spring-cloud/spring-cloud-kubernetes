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

package org.springframework.cloud.kubernetes.client;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.kubernetes.client.example.App;
import org.springframework.cloud.kubernetes.commons.EnvReader;
import org.springframework.context.annotation.Bean;

/**
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = { App.class, ActuatorEnabledFailFastExceptionTest.ActuatorConfig.class },
		properties = { "management.endpoint.health.show-details=always",
				"management.endpoint.health.show-components=always", "management.endpoints.web.exposure.include=health",
				"spring.main.cloud-platform=KUBERNETES" })
class ActuatorEnabledFailFastExceptionTest {

	private static final boolean FAIL_FAST = true;

	private static MockedStatic<EnvReader> envReaderMockedStatic;

	private static MockedStatic<Paths> pathsMockedStatic;

	private static final CoreV1Api coreV1Api = Mockito.mock(CoreV1Api.class);

	@Autowired
	private KubernetesClientHealthIndicator healthIndicator;

	@AfterEach
	void afterEach() {
		envReaderMockedStatic.close();
		pathsMockedStatic.close();
	}

	@Test
	void test() throws ApiException {
		Health health = healthIndicator.getHealth(true);
		Assertions.assertEquals(health.getStatus(), Status.DOWN);
		Mockito.verify(coreV1Api).readNamespacedPod("host", "my-namespace", null);
	}

	private static void mocks() {
		envReaderMockedStatic = Mockito.mockStatic(EnvReader.class);
		pathsMockedStatic = Mockito.mockStatic(Paths.class);

		envReaderMockedStatic.when(() -> EnvReader.getEnv(KubernetesClientPodUtils.KUBERNETES_SERVICE_HOST))
			.thenReturn("k8s-host");
		envReaderMockedStatic.when(() -> EnvReader.getEnv(KubernetesClientPodUtils.HOSTNAME)).thenReturn("host");

		Path serviceAccountTokenPath = Mockito.mock(Path.class);
		File serviceAccountTokenFile = Mockito.mock(File.class);
		Mockito.when(serviceAccountTokenPath.toFile()).thenReturn(serviceAccountTokenFile);
		Mockito.when(serviceAccountTokenFile.exists()).thenReturn(true);
		pathsMockedStatic.when(() -> Paths.get(Config.SERVICEACCOUNT_TOKEN_PATH)).thenReturn(serviceAccountTokenPath);

		Path serviceAccountCAPath = Mockito.mock(Path.class);
		File serviceAccountCAFile = Mockito.mock(File.class);
		Mockito.when(serviceAccountCAPath.toFile()).thenReturn(serviceAccountCAFile);
		Mockito.when(serviceAccountCAFile.exists()).thenReturn(true);
		pathsMockedStatic.when(() -> Paths.get(Config.SERVICEACCOUNT_CA_PATH)).thenReturn(serviceAccountCAPath);
	}

	@TestConfiguration
	static class ActuatorConfig {

		// will be created "instead" of
		// KubernetesClientAutoConfiguration::kubernetesPodUtils
		@Bean
		KubernetesClientPodUtils kubernetesPodUtils() throws ApiException {

			mocks();

			Mockito.when(coreV1Api.readNamespacedPod("host", "my-namespace", null))
				.thenThrow(new RuntimeException("just because"));

			return new KubernetesClientPodUtils(coreV1Api, "my-namespace", FAIL_FAST);
		}

	}

}
