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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesBatchRead;
import org.springframework.cloud.kubernetes.commons.config.Constants;

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

	private static final CoreV1Api CORE_V1_API = mock(CoreV1Api.class);

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST = new V1ConfigMapList()
		.addItemsItem(buildConfigMap("gateway", "default"));

	private static final V1ConfigMapList CONFIGMAP_TEAM_A_LIST = new V1ConfigMapList()
		.addItemsItem(buildConfigMap("stores", "team-a"));

	private static final V1ConfigMapList CONFIGMAP_TEAM_B_LIST = new V1ConfigMapList()
		.addItemsItem(buildConfigMap("orders", "team-b"));

	private static final V1SecretList SECRET_DEFAULT_LIST = new V1SecretList()
		.addItemsItem(buildSecret("gateway", "default"));

	private static final V1SecretList SECRET_TEAM_A_LIST = new V1SecretList()
		.addItemsItem(buildSecret("stores", "team-a"));

	private static final V1SecretList SECRET_TEAM_B_LIST = new V1SecretList()
		.addItemsItem(buildSecret("orders", "team-b"));

	private static final KubernetesConfigServerProperties PROPERTIES = properties();

	@BeforeAll
	static void beforeAll() throws ApiException {
		CoreV1Api.APIlistNamespacedConfigMapRequest defaultRequest = mock(
				CoreV1Api.APIlistNamespacedConfigMapRequest.class);
		when(defaultRequest.execute()).thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(CORE_V1_API.listNamespacedConfigMap(eq("default"))).thenReturn(defaultRequest);
		CoreV1Api.APIlistNamespacedConfigMapRequest teamARequest = mock(
				CoreV1Api.APIlistNamespacedConfigMapRequest.class);
		when(teamARequest.execute()).thenReturn(CONFIGMAP_TEAM_A_LIST);
		when(CORE_V1_API.listNamespacedConfigMap(eq("team-a"))).thenReturn(teamARequest);
		CoreV1Api.APIlistNamespacedConfigMapRequest teamBRequest = mock(
				CoreV1Api.APIlistNamespacedConfigMapRequest.class);
		when(teamBRequest.execute()).thenReturn(CONFIGMAP_TEAM_B_LIST);
		when(CORE_V1_API.listNamespacedConfigMap(eq("team-b"))).thenReturn(teamBRequest);

		CoreV1Api.APIlistNamespacedSecretRequest defaultSecretRequest = mock(
				CoreV1Api.APIlistNamespacedSecretRequest.class);
		when(defaultSecretRequest.execute()).thenReturn(SECRET_DEFAULT_LIST);
		when(CORE_V1_API.listNamespacedSecret(eq("default"))).thenReturn(defaultSecretRequest);
		CoreV1Api.APIlistNamespacedSecretRequest teamASecretRequest = mock(
				CoreV1Api.APIlistNamespacedSecretRequest.class);
		when(teamASecretRequest.execute()).thenReturn(SECRET_TEAM_A_LIST);
		when(CORE_V1_API.listNamespacedSecret(eq("team-a"))).thenReturn(teamASecretRequest);
		CoreV1Api.APIlistNamespacedSecretRequest teamBSecretRequest = mock(
				CoreV1Api.APIlistNamespacedSecretRequest.class);
		when(teamBSecretRequest.execute()).thenReturn(SECRET_TEAM_B_LIST);
		when(CORE_V1_API.listNamespacedSecret(eq("team-b"))).thenReturn(teamBSecretRequest);
	}

	@AfterEach
	void afterEach() {
		KubernetesClientSourcesBatchRead.discardConfigMaps();
		KubernetesClientSourcesBatchRead.discardSecrets();
	}

	@BeforeEach
	void beforeEach() {
		KubernetesClientSourcesBatchRead.discardConfigMaps();
		KubernetesClientSourcesBatchRead.discardSecrets();
	}

	@Test
	void whenCurrentAndExtraNamespacesAddedThenAllConfigMapsAreIncluded() {
		KubernetesConfigServerProperties kubernetesConfigServerProperties = new KubernetesConfigServerProperties();
		kubernetesConfigServerProperties.setConfigMapNamespaces("default,team-a,team-b");

		KubernetesPropertySourceSupplier kubernetesPropertySourceSupplier = new KubernetesConfigServerAutoConfiguration()
			.configMapPropertySourceSupplier(kubernetesConfigServerProperties);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(CORE_V1_API,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default", PROPERTIES);

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

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(CORE_V1_API,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default", PROPERTIES);

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

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(CORE_V1_API,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default", PROPERTIES);

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

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(CORE_V1_API,
				Collections.singletonList(kubernetesPropertySourceSupplier), "default", PROPERTIES);

		Environment environmentGateway = environmentRepository.findOne("gateway", "", "");
		assertThat(environmentGateway.getPropertySources().size()).isEqualTo(0);

		Environment environmentStores = environmentRepository.findOne("stores", "", "");
		assertThat(environmentStores.getPropertySources().size()).isEqualTo(1);

		Environment environmentOrders = environmentRepository.findOne("orders", "", "");
		assertThat(environmentOrders.getPropertySources().size()).isEqualTo(1);
	}

	private static V1ConfigMap buildConfigMap(String name, String namespace) {
		return new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(name).withNamespace(namespace).build())
			.addToData(Constants.APPLICATION_PROPERTIES, """
					dummy.property.string=%s
					""".formatted(name))
			.build();
	}

	private static V1Secret buildSecret(String name, String namespace) {
		return new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName(name).withNamespace(namespace).build())
			.addToData("password", "p455w0rd".getBytes())
			.addToData("username", "user".getBytes())
			.build();
	}

	private static KubernetesConfigServerProperties properties() {
		KubernetesConfigServerProperties properties = new KubernetesConfigServerProperties();
		properties.setOrder(1);
		return properties;
	}

}
