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

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.config.MountConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.MountSecretPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySource;
import org.springframework.cloud.kubernetes.fabric8.config.Fabric8SecretsPropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceProvider.configMapPropertySource;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigMapPropertySourceProvider.secretPropertySource;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8ConfigReloadUtilTests {

	private static KubernetesClient kubernetesClient;

	@BeforeAll
	static void beforeAll() {
		kubernetesClient.configMaps().inNamespace("default").resource(new ConfigMapBuilder().build());
	}

	@AfterAll
	static void afterAll() {
		kubernetesClient.configMaps().inAnyNamespace().delete();
	}

	/**
	 * isInstance configmap matches.
	 */
	@Test
	void testIsInstanceConfigMapPasses() {

		MockEnvironment environment = new MockEnvironment();
		Fabric8ConfigMapPropertySource configMapPropertySource = configMapPropertySource(kubernetesClient);
		environment.getPropertySources().addFirst(configMapPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(Fabric8ConfigMapPropertySource.class, environment);

		assertThat(propertySources).hasSize(1);
	}

	/**
	 * isInstance secret matches.
	 */
	@Test
	void testIsInstanceSecretPasses() {

		MockEnvironment environment = new MockEnvironment();
		Fabric8SecretsPropertySource secretsPropertySource = secretPropertySource(kubernetesClient);
		environment.getPropertySources().addFirst(secretsPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(Fabric8SecretsPropertySource.class, environment);

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
		Fabric8ConfigMapPropertySource configMapPropertySource = configMapPropertySource(kubernetesClient);
		MountSecretPropertySource mountSecretPropertySource = new MountSecretPropertySource(
				new SourceData("mountSecretPropertySource", Map.of("a", "b")));

		// both are present in environment
		environment.getPropertySources().addFirst(configMapPropertySource);
		environment.getPropertySources().addFirst(mountSecretPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(Fabric8ConfigMapPropertySource.class, environment);

		// the mount secret one is not taken
		assertThat(propertySources).hasSize(1);
		assertThat(propertySources.get(0)).isInstanceOf(Fabric8ConfigMapPropertySource.class);

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
		Fabric8SecretsPropertySource secretsPropertySource = secretPropertySource(kubernetesClient);
		MountConfigMapPropertySource mountConfigMapPropertySource = new MountConfigMapPropertySource(
				"mountConfigMapPropertySource", Map.of("a", "b"));

		// both are present in environment
		environment.getPropertySources().addFirst(secretsPropertySource);
		environment.getPropertySources().addFirst(mountConfigMapPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(Fabric8SecretsPropertySource.class, environment);

		// the mount configmap one is not taken
		assertThat(propertySources).hasSize(1);
		assertThat(propertySources.get(0)).isInstanceOf(Fabric8SecretsPropertySource.class);

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
		Fabric8ConfigMapPropertySource configMapPropertySource = configMapPropertySource(kubernetesClient);
		MountConfigMapPropertySource mountConfigMapPropertySource = new MountConfigMapPropertySource(
				"mountConfigMapPropertySource", Map.of("a", "b"));

		// both are present in environment
		environment.getPropertySources().addFirst(configMapPropertySource);
		environment.getPropertySources().addFirst(mountConfigMapPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(Fabric8ConfigMapPropertySource.class, environment);

		// both are taken
		assertThat(propertySources).hasSize(2);
		assertThat(propertySources.get(0)).isInstanceOf(MountConfigMapPropertySource.class);
		assertThat(propertySources.get(1)).isInstanceOf(Fabric8ConfigMapPropertySource.class);
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
		Fabric8SecretsPropertySource secretsPropertySource = secretPropertySource(kubernetesClient);
		MountSecretPropertySource mountSecretPropertySource = new MountSecretPropertySource(
				new SourceData("mountSecretPropertySource", Map.of("a", "b")));

		// both are present in environment
		environment.getPropertySources().addFirst(secretsPropertySource);
		environment.getPropertySources().addFirst(mountSecretPropertySource);

		List<MapPropertySource> propertySources = ConfigReloadUtil
			.findPropertySources(Fabric8SecretsPropertySource.class, environment);

		// both are taken
		assertThat(propertySources).hasSize(2);
		assertThat(propertySources.get(0)).isInstanceOf(MountSecretPropertySource.class);
		assertThat(propertySources.get(1)).isInstanceOf(Fabric8SecretsPropertySource.class);
	}

}
