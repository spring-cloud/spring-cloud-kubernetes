/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.EnvReader;
import org.springframework.cloud.kubernetes.fabric8.Fabric8PodUtils;

@SuppressWarnings("unchecked")
class Fabric8PodUtilsTest {

	private static final String KUBERNETES_SERVICE_HOST = Fabric8PodUtils.KUBERNETES_SERVICE_HOST;

	private static final String HOSTNAME = Fabric8PodUtils.HOSTNAME;

	private static final String SERVICE_ACCOUNT_TOKEN_PATH = Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH;

	private static final String SERVICE_ACCOUNT_CERT_PATH = Config.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH;

	private static final String POD_HOSTNAME = "pod-hostname";

	private static final String HOST = "10.1.1.1";

	private final KubernetesClient client = Mockito.mock(KubernetesClient.class);

	private final Path tokenPath = Mockito.mock(Path.class);

	private final File tokenFile = Mockito.mock(File.class);

	private final Path certPath = Mockito.mock(Path.class);

	private final File certFile = Mockito.mock(File.class);

	private final MixedOperation<Pod, PodList, PodResource> mixed = Mockito.mock(MixedOperation.class);

	private final Pod pod = Mockito.mock(Pod.class);

	private final PodResource podResource = Mockito.mock(PodResource.class);

	private MockedStatic<EnvReader> envReader;

	private MockedStatic<Paths> paths;

	@BeforeEach
	void before() {
		envReader = Mockito.mockStatic(EnvReader.class);
		paths = Mockito.mockStatic(Paths.class);
	}

	@AfterEach
	void after() {
		envReader.close();
		paths.close();
	}

	@Test
	void constructorThrowsIllegalArgumentExceptionWhenKubeClientNull() {
		// expect an IllegalArgumentException if KubernetesClient argument is
		// null
		Assertions.assertThrows(IllegalArgumentException.class, () -> new Fabric8PodUtils(null));
	}

	@Test
	void serviceHostNotPresent() {
		mockHost(null);
		Fabric8PodUtils util = new Fabric8PodUtils(client);
		Supplier<Pod> sup = util.currentPod();
		Assertions.assertNull(sup.get());
		Assertions.assertFalse(util.isInsideKubernetes());
	}

	@Test
	void hostnameNotPresent() {
		mockHost(HOST);
		mockHostname(null);
		Fabric8PodUtils util = new Fabric8PodUtils(client);
		Supplier<Pod> sup = util.currentPod();
		Assertions.assertNull(sup.get());
		Assertions.assertFalse(util.isInsideKubernetes());
	}

	@Test
	void serviceAccountPathNotPresent() {
		mockTokenPath(false);
		mockHostname(HOST);
		mockHostname(POD_HOSTNAME);
		Fabric8PodUtils util = new Fabric8PodUtils(client);
		Supplier<Pod> sup = util.currentPod();
		Assertions.assertNull(sup.get());
		Assertions.assertFalse(util.isInsideKubernetes());
	}

	@Test
	void serviceAccountCertPathNotPresent() {
		mockTokenPath(true);
		mockCertPath(false);
		mockHostname(HOST);
		mockHostname(POD_HOSTNAME);
		Fabric8PodUtils util = new Fabric8PodUtils(client);
		Supplier<Pod> sup = util.currentPod();
		Assertions.assertNull(sup.get());
		Assertions.assertFalse(util.isInsideKubernetes());
	}

	@Test
	void allPresent() {
		mockTokenPath(true);
		mockCertPath(true);
		mockHost(HOST);
		mockHostname(POD_HOSTNAME);
		mockPodResult();
		Fabric8PodUtils util = new Fabric8PodUtils(client);
		Supplier<Pod> sup = util.currentPod();
		Assertions.assertNotNull(sup.get());
		Assertions.assertTrue(util.isInsideKubernetes());
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

	private void mockPodResult() {
		Mockito.when(client.pods()).thenReturn(mixed);
		Mockito.when(mixed.withName(POD_HOSTNAME)).thenReturn(podResource);
		Mockito.when(podResource.get()).thenReturn(pod);
	}

}
