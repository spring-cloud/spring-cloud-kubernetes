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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1SecretList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigContext;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSourcesBatchRead;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.ReadType;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class KubernetesEnvironmentRepositoryTests {

	private static final List<KubernetesPropertySourceSupplier> KUBERNETES_PROPERTY_SOURCE_SUPPLIER = new ArrayList<>();

	private static final V1ConfigMapList CONFIGMAP_ONE_LIST = Util.yaml("configmap-one-list.yaml",
			V1ConfigMapList.class);

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST = Util.yaml("configmap-default-list.yaml",
			V1ConfigMapList.class);

	private static final V1ConfigMapList CONFIGMAP_DEV_LIST = Util.yaml("configmap-dev-list.yaml",
			V1ConfigMapList.class);

	private static final V1SecretList SECRET_LIST = Util.yaml("secret-one-list.yaml", V1SecretList.class);

	private static final KubernetesConfigServerProperties PROPERTIES = properties();

	@BeforeAll
	static void before() {
		KUBERNETES_PROPERTY_SOURCE_SUPPLIER.clear();

		KUBERNETES_PROPERTY_SOURCE_SUPPLIER.add((coreApi, applicationName, namespace, springEnv) -> {
			List<MapPropertySource> propertySources = new ArrayList<>();

			NormalizedSource defaultSource = new NamedConfigMapNormalizedSource(applicationName, "default", false,
					true);
			KubernetesClientConfigContext defaultContext = new KubernetesClientConfigContext(coreApi, defaultSource,
					"default", springEnv, true, ReadType.BATCH);
			propertySources.add(new KubernetesClientConfigMapPropertySource(defaultContext));

			if ("stores".equals(applicationName) && "dev".equals(namespace)) {
				NormalizedSource devSource = new NamedConfigMapNormalizedSource(applicationName, "dev", false, true);
				KubernetesClientConfigContext devContext = new KubernetesClientConfigContext(coreApi, devSource, "dev",
						springEnv, true, ReadType.BATCH);
				propertySources.add(new KubernetesClientConfigMapPropertySource(devContext));
			}
			return propertySources;
		});

		KUBERNETES_PROPERTY_SOURCE_SUPPLIER.add((coreApi, applicationName, namespace, springEnv) -> {
			List<MapPropertySource> propertySources = new ArrayList<>();

			NormalizedSource source = new NamedSecretNormalizedSource(applicationName, "default", false, true);
			KubernetesClientConfigContext context = new KubernetesClientConfigContext(coreApi, source, "default",
					springEnv, true, ReadType.BATCH);

			propertySources.add(new KubernetesClientSecretsPropertySource(context));
			return propertySources;
		});
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

	/**
	 * <pre>
	 * Given application=application and an empty profile,
	 * the repository loads:
	 * - the application ConfigMap
	 * - the application Secret
	 * </pre>
	 */
	@Test
	@SuppressWarnings("unchecked")
	void testApplicationCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);

		Environment environment = environmentRepository.findOne("application", "", "");

		Map<String, Map<String, Object>> result = environment.getPropertySources()
			.stream()
			.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

		assertThat(result.keySet()).containsExactly("secret.application.default", "configmap.application.default");

		Map<String, Object> fromConfigMap = result.get("configmap.application.default");
		Map<String, Object> fromSecret = result.get("secret.application.default");

		assertThat(fromConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromSecret).containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));
	}

	/**
	 * <pre>
	 * Given application=application and an empty profile,
	 * the repository loads:
	 * - the application ConfigMap
	 * - the application Secret
	 * and preserves the configured repository order.
	 * </pre>
	 */
	@Test
	@SuppressWarnings("unchecked")
	void testApplicationCaseWithNewConstructor() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		KubernetesConfigServerProperties properties = mock(KubernetesConfigServerProperties.class);
		when(properties.getOrder()).thenReturn(0);
		mockRequests(coreApi);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", properties);

		Environment environment = environmentRepository.findOne("application", "", "");

		Map<String, Map<String, Object>> result = environment.getPropertySources()
			.stream()
			.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

		assertThat(result.keySet()).containsExactly("secret.application.default", "configmap.application.default");

		Map<String, Object> fromConfigMap = result.get("configmap.application.default");
		Map<String, Object> fromSecret = result.get("secret.application.default");

		assertThat(fromConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromSecret).containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));

		assertThat(environmentRepository.getOrder()).isEqualTo(0);
	}

	/**
	 * <pre>
	 * Given application=stores and an empty profile,
	 * the repository loads:
	 * - the stores ConfigMap and Secret
	 * - the application ConfigMap and Secret as additional fallback sources
	 * </pre>
	 */
	@Test
	@SuppressWarnings("unchecked")
	void testStoresCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);

		Environment environment = environmentRepository.findOne("stores", "", "");

		Map<String, Map<String, Object>> result = environment.getPropertySources()
			.stream()
			.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

		assertThat(result.keySet()).containsExactly("configmap.stores.default", "secret.application.default",
				"configmap.application.default", "secret.stores.default");

		Map<String, Object> fromApplicationConfigMap = result.get("configmap.application.default");
		Map<String, Object> fromApplicationSecret = result.get("secret.application.default");
		Map<String, Object> fromStoresConfigMap = result.get("configmap.stores.default");
		Map<String, Object> fromStoresSecret = result.get("secret.stores.default");

		assertThat(fromApplicationConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromApplicationSecret)
			.containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));

		assertThat(fromStoresConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromStoresSecret)
			.containsExactlyInAnyOrderEntriesOf(Map.of("username", "stores", "password", "password-from-stores"));
	}

	/**
	 * <pre>
	 * Given application=stores and profile=dev,
	 * the repository loads:
	 * - the stores-dev ConfigMap and Secret
	 * - the stores ConfigMap and Secret
	 * - the application ConfigMap and Secret as fallbacks
	 * </pre>
	 */
	@Test
	@SuppressWarnings("unchecked")
	void testStoresProfileCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);

		Environment environment = environmentRepository.findOne("stores", "dev", "");

		Map<String, Map<String, Object>> result = environment.getPropertySources()
			.stream()
			.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

		assertThat(result.keySet()).containsExactly("configmap.stores.stores-dev.default", "configmap.stores.default",
				"secret.application.default", "secret.stores.stores-dev.default", "configmap.application.default",
				"secret.stores.default");

		Map<String, Object> fromApplicationConfigMap = result.get("configmap.application.default");
		Map<String, Object> fromApplicationSecret = result.get("secret.application.default");
		Map<String, Object> fromStoresProfileConfigMap = result.get("configmap.stores.stores-dev.default");
		Map<String, Object> fromStoresSecret = result.get("secret.stores.default");
		Map<String, Object> fromStoresProfileSecret = result.get("secret.stores.stores-dev.default");
		Map<String, Object> fromStoresConfigMap = result.get("configmap.stores.default");

		assertThat(fromApplicationConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromApplicationSecret)
			.containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));

		assertThat(fromStoresProfileSecret).containsExactlyInAnyOrderEntriesOf(
				Map.of("username", "stores-dev", "password", "password-from-stores-dev"));

		assertThat(fromStoresProfileConfigMap).containsExactlyInAnyOrderEntriesOf(Map.of("dummy.property.int2", 2,
				"dummy.property.bool2", false, "dummy.property.string1", "a", "dummy.property.string2", "b"));

		assertThat(fromStoresConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromStoresSecret)
			.containsExactlyInAnyOrderEntriesOf(Map.of("username", "stores", "password", "password-from-stores"));
	}

	/**
	 * <pre>
	 * Given application=stores-dev and an empty profile,
	 * the repository loads:
	 * - the stores-dev ConfigMap and Secret
	 * - the application ConfigMap and Secret as fallbacks
	 * </pre>
	 */
	@Test
	@SuppressWarnings("unchecked")
	void testApplicationPropertiesAnSecretsOverride() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);

		Environment environment = environmentRepository.findOne("stores-dev", "", "");

		Map<String, Map<String, Object>> result = environment.getPropertySources()
			.stream()
			.collect(Collectors.toMap(PropertySource::getName,
					propertySource -> (Map<String, Object>) propertySource.getSource()));

		assertThat(result.keySet()).containsExactly("secret.application.default", "configmap.application.default",
				"configmap.stores-dev.default", "secret.stores-dev.default");

		Map<String, Object> fromApplicationConfigMap = result.get("configmap.application.default");
		Map<String, Object> fromApplicationSecret = result.get("secret.application.default");
		Map<String, Object> fromStoresDevConfigMap = result.get("configmap.stores-dev.default");
		Map<String, Object> fromStoresDevSecret = result.get("secret.stores-dev.default");

		assertThat(fromApplicationConfigMap).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromStoresDevConfigMap).containsExactlyInAnyOrderEntriesOf(Map.of("dummy.property.int2", 2,
				"dummy.property.bool2", false, "dummy.property.string1", "a", "dummy.property.string2", "b"));

		assertThat(fromApplicationSecret)
			.containsExactlyInAnyOrderEntriesOf(Map.of("username", "user", "password", "p455w0rd"));

		assertThat(fromStoresDevSecret).containsExactlyInAnyOrderEntriesOf(
				Map.of("username", "stores-dev", "password", "password-from-stores-dev"));
	}

	/**
	 * <pre>
	 * Verifies ConfigMap resolution across:
	 * - the default lookup
	 * - a single active profile
	 * - multiple active profiles in precedence order
	 * </pre>
	 */
	@Test
	@SuppressWarnings("unchecked")
	void testSingleConfigMapMultipleSources() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		CoreV1Api.APIlistNamespacedConfigMapRequest configMapRequest = mock(
				CoreV1Api.APIlistNamespacedConfigMapRequest.class);
		when(configMapRequest.execute()).thenReturn(CONFIGMAP_ONE_LIST);
		when(coreApi.listNamespacedConfigMap(eq("default"))).thenReturn(configMapRequest);

		CoreV1Api.APIlistNamespacedSecretRequest secretRequest = mock(CoreV1Api.APIlistNamespacedSecretRequest.class);
		when(secretRequest.execute()).thenReturn(new V1SecretList());
		when(coreApi.listNamespacedSecret(eq("default"))).thenReturn(secretRequest);

		List<KubernetesPropertySourceSupplier> suppliers = new ArrayList<>();
		suppliers.add((coreV1Api, name, namespace, environment) -> {
			List<MapPropertySource> propertySources = new ArrayList<>();
			NormalizedSource devSource = new NamedConfigMapNormalizedSource(name, namespace, false,
					ConfigUtils.Prefix.DEFAULT, true, true);
			KubernetesClientConfigContext devContext = new KubernetesClientConfigContext(coreApi, devSource, "default",
					environment, true, ReadType.BATCH);
			KubernetesClientConfigMapPropertySource configMapPropertySource = new KubernetesClientConfigMapPropertySource(
					devContext);
			if (!configMapPropertySource.isEmpty()) {
				propertySources.add(configMapPropertySource);
			}
			return propertySources;
		});

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi, suppliers,
				"default", PROPERTIES);

		Environment environment = environmentRepository.findOne("stores", "", "");
		assertThat(environment.getPropertySources()).singleElement()
			.satisfies(propertySource -> assertThat(propertySource.getName())
				.isEqualTo("configmap.stores.default.default"));

		environment = environmentRepository.findOne("stores", "dev", "");
		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("configmap.stores.default.dev");
		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("configmap.stores.default.default");

		environment = environmentRepository.findOne("stores", "dev,prod", "");
		assertThat(environment.getPropertySources()).hasSize(3);

		PropertySource first = environment.getPropertySources().get(0);
		assertThat(first.getName()).isEqualTo("configmap.stores.default.prod");
		Map<String, Object> firstSource = (Map<String, Object>) first.getSource();
		assertThat(firstSource).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 3, "dummy.property.bool2", true, "dummy.property.string2", "prod"));

		PropertySource second = environment.getPropertySources().get(1);
		assertThat(second.getName()).isEqualTo("configmap.stores.default.dev");
		Map<String, Object> secondSource = (Map<String, Object>) second.getSource();
		assertThat(secondSource).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", false, "dummy.property.string2", "dev"));

		PropertySource third = environment.getPropertySources().get(2);
		assertThat(third.getName()).isEqualTo("configmap.stores.default.default");
		Map<String, Object> thirdSource = (Map<String, Object>) third.getSource();
		assertThat(thirdSource).containsExactlyInAnyOrderEntriesOf(
				Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));
	}

	private static KubernetesConfigServerProperties properties() {
		KubernetesConfigServerProperties properties = new KubernetesConfigServerProperties();
		properties.setOrder(1);
		return properties;
	}

	private void mockRequests(CoreV1Api coreApi) throws ApiException {
		CoreV1Api.APIlistNamespacedConfigMapRequest defaultConfigRequest = mock(
				CoreV1Api.APIlistNamespacedConfigMapRequest.class);
		when(coreApi.listNamespacedConfigMap(eq("default"))).thenReturn(defaultConfigRequest);
		when(defaultConfigRequest.execute()).thenReturn(CONFIGMAP_DEFAULT_LIST);

		CoreV1Api.APIlistNamespacedSecretRequest secretRequest = mock(CoreV1Api.APIlistNamespacedSecretRequest.class);
		when(coreApi.listNamespacedSecret(eq("default"))).thenReturn(secretRequest);
		when(secretRequest.execute()).thenReturn(SECRET_LIST);

		CoreV1Api.APIlistNamespacedConfigMapRequest devConfigRequest = mock(
				CoreV1Api.APIlistNamespacedConfigMapRequest.class);
		when(coreApi.listNamespacedConfigMap(eq("dev"))).thenReturn(devConfigRequest);
		when(devConfigRequest.execute()).thenReturn(CONFIGMAP_DEV_LIST);
	}

}
