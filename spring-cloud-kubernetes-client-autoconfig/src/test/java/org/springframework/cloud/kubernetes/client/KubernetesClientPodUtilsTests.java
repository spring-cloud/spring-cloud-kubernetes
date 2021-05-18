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

package org.springframework.cloud.kubernetes.client;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.EnvReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author wind57
 */
public class KubernetesClientPodUtilsTests {

	private static final String KUBERNETES_SERVICE_HOST = KubernetesClientPodUtils.KUBERNETES_SERVICE_HOST;

	private static final String HOSTNAME = KubernetesClientPodUtils.HOSTNAME;

	private static final String SERVICE_ACCOUNT_TOKEN_PATH = Config.SERVICEACCOUNT_TOKEN_PATH;

	private static final String SERVICE_ACCOUNT_CERT_PATH = Config.SERVICEACCOUNT_CA_PATH;

	private static final String POD_HOSTNAME = "pod-hostname";

	private static final String HOST = "10.1.1.1";

	private static final V1Pod POD = new V1Pod();

	private final CoreV1Api client = Mockito.mock(CoreV1Api.class);

	private final Path tokenPath = Mockito.mock(Path.class);

	private final File tokenFile = Mockito.mock(File.class);

	private final Path certPath = Mockito.mock(Path.class);

	private final File certFile = Mockito.mock(File.class);

	private MockedStatic<EnvReader> envReader;

	private MockedStatic<Paths> paths;

	@BeforeEach
	public void before() {
		envReader = Mockito.mockStatic(EnvReader.class);
		paths = Mockito.mockStatic(Paths.class);
	}

	@AfterEach
	public void after() {
		envReader.close();
		paths.close();
	}

	@Test
	public void constructorThrowsIllegalArgumentExceptionWhenKubeClientIsNull() {
		assertThatThrownBy(() -> new KubernetesClientPodUtils(null, "namespace"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Must provide an instance of KubernetesClient");
	}

	@Test
	public void serviceHostNotPresent() {
		mockHost(null);

		KubernetesClientPodUtils util = new KubernetesClientPodUtils(client, "namespace");
		Supplier<V1Pod> sup = util.currentPod();
		assertSupplierAndClient(sup, util);
	}

	@Test
	public void hostNameNotPresent() {
		mockHost(HOST);
		mockHostname(null);

		KubernetesClientPodUtils util = new KubernetesClientPodUtils(client, "namespace");
		Supplier<V1Pod> sup = util.currentPod();
		assertSupplierAndClient(sup, util);
	}

	@Test
	public void serviceAccountPathNotPresent() {
		mockTokenPath(false);
		mockHostname(HOST);

		KubernetesClientPodUtils util = new KubernetesClientPodUtils(client, "namespace");
		Supplier<V1Pod> sup = util.currentPod();
		assertSupplierAndClient(sup, util);
	}

	@Test
	public void serviceAccountCertPathNotPresent() {
		mockTokenPath(true);
		mockCertPath(false);
		mockHostname(HOST);

		KubernetesClientPodUtils util = new KubernetesClientPodUtils(client, "namespace");
		Supplier<V1Pod> sup = util.currentPod();
		assertSupplierAndClient(sup, util);
	}

	@Test
	public void allPresent() throws ApiException {
		mockTokenPath(true);
		mockCertPath(true);
		mockHost(HOST);
		mockHostname(POD_HOSTNAME);
		mockPodResult();

		KubernetesClientPodUtils util = new KubernetesClientPodUtils(client, "namespace");
		Supplier<V1Pod> sup = util.currentPod();
		assertThat(sup.get()).isNotNull();
		assertThat(util.isInsideKubernetes()).isTrue();
	}

	private void assertSupplierAndClient(Supplier<V1Pod> sup, KubernetesClientPodUtils util) {
		assertThat(sup.get()).isNull();
		assertThat(util.isInsideKubernetes()).isFalse();
	}

	private void mockHost(String host) {
		envReader.when(() -> EnvReader.getEnv(KUBERNETES_SERVICE_HOST)).thenReturn(host);
	}

	private void mockHostname(String name) {
		envReader.when(() -> EnvReader.getEnv(HOSTNAME)).thenReturn(name);
	}

	private void mockTokenPath(boolean result) {
		Mockito.when(tokenPath.toFile()).thenReturn(tokenFile);
		Mockito.when(tokenFile.exists()).thenReturn(result);
		paths.when(() -> Paths.get(SERVICE_ACCOUNT_TOKEN_PATH)).thenReturn(tokenPath);
	}

	private void mockCertPath(boolean result) {
		Mockito.when(certPath.toFile()).thenReturn(certFile);
		Mockito.when(certFile.exists()).thenReturn(result);
		paths.when(() -> Paths.get(SERVICE_ACCOUNT_CERT_PATH)).thenReturn(certPath);
	}

	private void mockPodResult() throws ApiException {
		Mockito.when(client.readNamespacedPod(POD_HOSTNAME, "namespace", null, null, null)).thenReturn(POD);
	}

}
