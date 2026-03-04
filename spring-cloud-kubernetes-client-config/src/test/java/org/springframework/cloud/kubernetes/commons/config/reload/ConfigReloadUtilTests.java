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

package org.springframework.cloud.kubernetes.commons.config.reload;

import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ConfigMapListBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigContext;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.commons.config.MountConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.MountSecretPropertySource;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class ConfigReloadUtilTests {

	private static final V1ConfigMapList CONFIGMAP_LIST = new V1ConfigMapListBuilder().build();

	private static final V1SecretList SECRET_LIST = new V1SecretListBuilder().build();

	private static WireMockServer wireMockServer;

	private static ApiClient apiClient;

	@BeforeAll
	static void beforeAll() {
		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		apiClient = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();

		stubFor(get("/api/v1/namespaces/default/configmaps")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(CONFIGMAP_LIST))));

		stubFor(get("/api/v1/namespaces/default/secrets")
			.willReturn(aResponse().withStatus(200).withBody(new JSON().serialize(SECRET_LIST))));

	}

	@AfterAll
	static void afterAll() {
		wireMockServer.stop();
	}

	/**
	 * isInstance configmap matches.
	 */
	@Test
	void testIsInstanceConfigMapPasses() {

		MockEnvironment environment = new MockEnvironment();
		KubernetesClientConfigMapPropertySource configMapPropertySource = configMapPropertySource();
		environment.getPropertySources().addFirst(configMapPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(KubernetesClientConfigMapPropertySource.class, environment);

		assertThat(propertySources).hasSize(1);
	}

	/**
	 * isInstance secret matches.
	 */
	@Test
	void testIsInstanceSecretPasses() {

		MockEnvironment environment = new MockEnvironment();
		KubernetesClientSecretsPropertySource secretsPropertySource = secretsPropertySource();
		environment.getPropertySources().addFirst(secretsPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(KubernetesClientSecretsPropertySource.class, environment);

		assertThat(propertySources).hasSize(1);
	}

	/**
	 * <pre>
	 *     - environment contains Fabric8ConfigMapPropertySource
	 *     - environment contains MountSecretPropertySource
	 *     - we search for Fabric8ConfigMapPropertySource
	 *
	 *     - the result is a single PropertySource of type Fabric8ConfigMapPropertySource,
	 *       the MountSecretPropertySource is not taken.
	 * </pre>
	 */
	@Test
	void testMountSecretPropertySourceNotTaken() {
		MockEnvironment environment = new MockEnvironment();
		KubernetesClientConfigMapPropertySource configMapPropertySource = configMapPropertySource();
		MountSecretPropertySource mountSecretPropertySource = new MountSecretPropertySource(
				new SourceData("mountSecretPropertySource", Map.of("a", "b")));

		// both are present in environment
		environment.getPropertySources().addFirst(configMapPropertySource);
		environment.getPropertySources().addFirst(mountSecretPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(KubernetesClientConfigMapPropertySource.class, environment);

		// the mount secret one is not taken
		assertThat(propertySources).hasSize(1);
		assertThat(propertySources.get(0)).isInstanceOf(KubernetesClientConfigMapPropertySource.class);

	}

	/**
	 * <pre>
	 *     - environment contains Fabric8SecretsPropertySource
	 *     - environment contains MountConfigMapPropertySource
	 *     - we search for Fabric8SecretsPropertySource
	 *
	 *     - the result is a single PropertySource of type Fabric8SecretsPropertySource,
	 *       the MountConfigMapPropertySource is not taken.
	 * </pre>
	 */
	@Test
	void testMountConfigMapPropertySourceNotTaken() {
		MockEnvironment environment = new MockEnvironment();
		KubernetesClientSecretsPropertySource secretsPropertySource = secretsPropertySource();
		MountConfigMapPropertySource mountConfigMapPropertySource = new MountConfigMapPropertySource(
				"mountConfigMapPropertySource", Map.of("a", "b"));

		// both are present in environment
		environment.getPropertySources().addFirst(secretsPropertySource);
		environment.getPropertySources().addFirst(mountConfigMapPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(KubernetesClientSecretsPropertySource.class, environment);

		// the mount configmap one is not taken
		assertThat(propertySources).hasSize(1);
		assertThat(propertySources.get(0)).isInstanceOf(KubernetesClientSecretsPropertySource.class);

	}

	/**
	 * <pre>
	 *     - environment contains Fabric8ConfigMapPropertySource
	 *     - environment contains MountConfigMapPropertySource
	 *     - we search for Fabric8ConfigMapPropertySource
	 *
	 *     - the result is two PropertySources: Fabric8ConfigMapPropertySource
	 *       and MountConfigMapPropertySource.
	 * </pre>
	 */
	@Test
	void testMountConfigMapPropertySourceTaken() {
		MockEnvironment environment = new MockEnvironment();
		KubernetesClientConfigMapPropertySource configMapPropertySource = configMapPropertySource();
		MountConfigMapPropertySource mountConfigMapPropertySource = new MountConfigMapPropertySource(
				"mountConfigMapPropertySource", Map.of("a", "b"));

		// both are present in environment
		environment.getPropertySources().addFirst(configMapPropertySource);
		environment.getPropertySources().addFirst(mountConfigMapPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(KubernetesClientConfigMapPropertySource.class, environment);

		// both are taken
		assertThat(propertySources).hasSize(2);
		assertThat(propertySources.get(0)).isInstanceOf(MountConfigMapPropertySource.class);
		assertThat(propertySources.get(1)).isInstanceOf(KubernetesClientConfigMapPropertySource.class);
	}

	/**
	 * <pre>
	 *     - environment contains Fabric8SecretsPropertySource
	 *     - environment contains MountSecretPropertySource
	 *     - we search for Fabric8SecretsPropertySource
	 *
	 *     - the result is two PropertySources: Fabric8SecretsPropertySource
	 *       and MountSecretPropertySource.
	 * </pre>
	 */
	@Test
	void testMountSecretPropertySourceTaken() {
		MockEnvironment environment = new MockEnvironment();
		KubernetesClientSecretsPropertySource secretsPropertySource = secretsPropertySource();
		MountSecretPropertySource mountSecretPropertySource = new MountSecretPropertySource(
				new SourceData("mountSecretPropertySource", Map.of("a", "b")));

		// both are present in environment
		environment.getPropertySources().addFirst(secretsPropertySource);
		environment.getPropertySources().addFirst(mountSecretPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(KubernetesClientSecretsPropertySource.class, environment);

		// both are taken
		assertThat(propertySources).hasSize(2);
		assertThat(propertySources.get(0)).isInstanceOf(MountSecretPropertySource.class);
		assertThat(propertySources.get(1)).isInstanceOf(KubernetesClientSecretsPropertySource.class);
	}

	private KubernetesClientConfigMapPropertySource configMapPropertySource() {
		CoreV1Api api = new CoreV1Api(apiClient);
		NamedConfigMapNormalizedSource namedSecretNormalizedSource = new NamedConfigMapNormalizedSource("configmap",
				"default", true, true);
		MockEnvironment environment = new MockEnvironment();

		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, namedSecretNormalizedSource,
				"default", environment);

		return new KubernetesClientConfigMapPropertySource(context);
	}

	private KubernetesClientSecretsPropertySource secretsPropertySource() {
		CoreV1Api api = new CoreV1Api(apiClient);
		NamedSecretNormalizedSource namedSecretNormalizedSource = new NamedSecretNormalizedSource("secret", "default",
				true, true);
		MockEnvironment environment = new MockEnvironment();

		KubernetesClientConfigContext context = new KubernetesClientConfigContext(api, namedSecretNormalizedSource,
				"default", environment);

		return new KubernetesClientSecretsPropertySource(context);
	}

}
