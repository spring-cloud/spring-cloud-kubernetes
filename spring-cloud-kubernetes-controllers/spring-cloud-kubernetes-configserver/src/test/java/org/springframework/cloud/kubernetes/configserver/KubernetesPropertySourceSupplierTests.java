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

package org.springframework.cloud.kubernetes.configserver;

import java.util.Collections;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for verifying the behavior of the {@link KubernetesPropertySourceSupplier} beans
 * for ConfigMaps and Secrets created by the auto-configuration.
 *
 * @author Thomas Vitale
 */
class KubernetesPropertySourceSupplierTests {

	private static final CoreV1Api coreApi = mock(CoreV1Api.class);

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST = new V1ConfigMapList()
			.addItemsItem(buildConfigMap("gateway", "tdefault"));

	private static final V1ConfigMapList CONFIGMAP_TEAM_A_LIST = new V1ConfigMapList()
			.addItemsItem(buildConfigMap("stores", "team-a"));

	private static final V1ConfigMapList CONFIGMAP_TEAM_B_LIST = new V1ConfigMapList()
			.addItemsItem(buildConfigMap("orders", "team-b"));

	private static final V1SecretList SECRET_DEFAULT_LIST = new V1SecretListBuilder()
			.addToItems(buildSecret("gateway", "default")).build();

	private static final V1SecretList SECRET_TEAM_A_LIST = new V1SecretListBuilder()
			.addToItems(buildSecret("stores", "team-a")).build();

	private static final V1SecretList SECRET_TEAM_B_LIST = new V1SecretListBuilder()
			.addToItems(buildSecret("orders", "team-b")).build();

	@BeforeAll
	public static void before() throws ApiException {
		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(coreApi.listNamespacedConfigMap(eq("team-a"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(CONFIGMAP_TEAM_A_LIST);
		when(coreApi.listNamespacedConfigMap(eq("team-b"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(CONFIGMAP_TEAM_B_LIST);

		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(SECRET_DEFAULT_LIST);
		when(coreApi.listNamespacedSecret(eq("team-a"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(SECRET_TEAM_A_LIST);
		when(coreApi.listNamespacedSecret(eq("team-b"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null))).thenReturn(SECRET_TEAM_B_LIST);
	}

	@Test
	void whenCurrentAndExtraNamespacesAddedThenAllConfigMapsAreIncluded() {
		KubernetesConfigServerProperties kubernetesConfigServerProperties = new KubernetesConfigServerProperties();
		kubernetesConfigServerProperties.setConfigMapNamespaces("default,team-a,team-b");

		KubernetesPropertySourceSupplier kubernetesPropertySourceSupplier = new KubernetesConfigServerAutoConfiguration()
				.configMapPropertySourceSupplier(kubernetesConfigServerProperties);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default");

		Environment environmentGateway = environmentRepository.findOne("gateway", "", "");
		assertThat(environmentGateway.getPropertySources().size()).isEqualTo(1);

		Environment environmentStores = environmentRepository.findOne("stores", "", "");
		assertThat(environmentStores.getPropertySources().size()).isEqualTo(1);

		Environment environmentOrders = environmentRepository.findOne("orders", "", "");
		assertThat(environmentOrders.getPropertySources().size()).isEqualTo(1);
	}

	@Test
	void whenExtraNamespacesAddedThenConfigMapsInCurrentNamespaceAreNotIncluded() {
		KubernetesConfigServerProperties kubernetesConfigServerProperties = new KubernetesConfigServerProperties();
		kubernetesConfigServerProperties.setConfigMapNamespaces("team-a,team-b");

		KubernetesPropertySourceSupplier kubernetesPropertySourceSupplier = new KubernetesConfigServerAutoConfiguration()
				.configMapPropertySourceSupplier(kubernetesConfigServerProperties);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default");

		Environment environmentGateway = environmentRepository.findOne("gateway", "", "");
		assertThat(environmentGateway.getPropertySources().size()).isEqualTo(0);

		Environment environmentStores = environmentRepository.findOne("stores", "", "");
		assertThat(environmentStores.getPropertySources().size()).isEqualTo(1);

		Environment environmentOrders = environmentRepository.findOne("orders", "", "");
		assertThat(environmentOrders.getPropertySources().size()).isEqualTo(1);
	}

	@Test
	void whenCurrentAndExtraNamespacesAddedThenAllSecretsAreIncluded() {
		KubernetesConfigServerProperties kubernetesConfigServerProperties = new KubernetesConfigServerProperties();
		kubernetesConfigServerProperties.setSecretsNamespaces("default,team-a,team-b");

		KubernetesPropertySourceSupplier kubernetesPropertySourceSupplier = new KubernetesConfigServerAutoConfiguration()
				.secretsPropertySourceSupplier(kubernetesConfigServerProperties);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default");

		Environment environmentGateway = environmentRepository.findOne("gateway", "", "");
		assertThat(environmentGateway.getPropertySources().size()).isEqualTo(1);

		Environment environmentStores = environmentRepository.findOne("stores", "", "");
		assertThat(environmentStores.getPropertySources().size()).isEqualTo(1);

		Environment environmentOrders = environmentRepository.findOne("orders", "", "");
		assertThat(environmentOrders.getPropertySources().size()).isEqualTo(1);
	}

	@Test
	void whenExtraNamespacesAddedThenSecretsInCurrentNamespaceAreNotIncluded() {
		KubernetesConfigServerProperties kubernetesConfigServerProperties = new KubernetesConfigServerProperties();
		kubernetesConfigServerProperties.setSecretsNamespaces("team-a,team-b");

		KubernetesPropertySourceSupplier kubernetesPropertySourceSupplier = new KubernetesConfigServerAutoConfiguration()
				.secretsPropertySourceSupplier(kubernetesConfigServerProperties);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default");

		Environment environmentGateway = environmentRepository.findOne("gateway", "", "");
		assertThat(environmentGateway.getPropertySources().size()).isEqualTo(0);

		Environment environmentStores = environmentRepository.findOne("stores", "", "");
		assertThat(environmentStores.getPropertySources().size()).isEqualTo(1);

		Environment environmentOrders = environmentRepository.findOne("orders", "", "");
		assertThat(environmentOrders.getPropertySources().size()).isEqualTo(1);
	}

	private static V1ConfigMap buildConfigMap(String name, String namespace) {
		return new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName(name).withNamespace(namespace).withResourceVersion("1")
						.build())
				.addToData("application.yaml", "dummy:\n  property:\n    string: \"" + name + "\"\n").build();
	}

	private static V1Secret buildSecret(String name, String namespace) {
		return new V1SecretBuilder()
				.withMetadata(new V1ObjectMetaBuilder().withName(name).withResourceVersion("0").withNamespace(namespace)
						.build())
				.addToData("password", "p455w0rd".getBytes()).addToData("username", "user".getBytes()).build();
	}

}
