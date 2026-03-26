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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.Yaml;
import org.assertj.core.api.Assertions;
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

	private static final V1ConfigMapList CONFIGMAP_ONE_LIST =
		yaml("configmap-one-list.yaml", V1ConfigMapList.class);

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST =
		yaml("configmap-default-list.yaml", V1ConfigMapList.class);

	private static final V1ConfigMapList CONFIGMAP_DEV_LIST =
		yaml("configmap-dev-list.yaml", V1ConfigMapList.class);

	private static final V1SecretList SECRET_LIST =
		yaml("secret-one-list.yaml", V1SecretList.class);

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

	@Test
	@SuppressWarnings("unchecked")
	void testApplicationCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);

		Environment environment = environmentRepository.findOne("application", "", "");

		Map<String, Map<String, Object>> result = environment.getPropertySources().stream().collect(Collectors.toMap(
			PropertySource::getName,
			propertySource -> (Map<String, Object>) propertySource.getSource()
		));

		assertThat(result.keySet()).containsExactlyInAnyOrder(
			"configmap.application.default", "secret.application.default"
		);

		Map<String, Object> fromConfigMap = result.get("configmap.application.default");
		Map<String, Object> fromSecret = result.get("secret.application.default");

		assertThat(fromConfigMap).containsAllEntriesOf(
			Map.of("dummy.property.int2", 1, "dummy.property.bool2", true, "dummy.property.string2", "a"));

		assertThat(fromSecret).containsAllEntriesOf(
			Map.of("username", "user", "password", "p455w0rd"));
	}

	@Test
	void testApplicationCaseWithNewConstructor() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		KubernetesConfigServerProperties properties = mock(KubernetesConfigServerProperties.class);
		when(properties.getOrder()).thenReturn(0);
		mockRequests(coreApi);

		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", properties);

		Environment environment = environmentRepository.findOne("application", "", "");

		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		environment.getPropertySources().forEach(propertySource -> {
			assertThat(propertySource.getName().equals("configmap.application.default")
					|| propertySource.getName().equals("secret.application.default"))
				.isTrue();
			if (propertySource.getName().equals("configmap.application.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(3);
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(1);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(true);
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("a");
			}
			if (propertySource.getName().equals("secrets.application.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(2);
				assertThat(propertySource.getSource().get("username")).isEqualTo("user");
				assertThat(propertySource.getSource().get("password")).isEqualTo("p455w0rd");
			}
		});

		assertThat(environmentRepository.getOrder()).isEqualTo(0);
	}

	@Test
	void testStoresCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);
		Environment environment = environmentRepository.findOne("stores", "", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(4);
		environment.getPropertySources().forEach(propertySource -> {
			assertThat(propertySource.getName().equals("configmap.application.default")
					|| propertySource.getName().equals("secret.application.default")
					|| propertySource.getName().equals("configmap.stores.default")
					|| propertySource.getName().equals("secret.stores.default"))
				.isTrue();
			if (propertySource.getName().equals("configmap.application.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(3);
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(1);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(true);
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("a");
			}
			if (propertySource.getName().equals("secret.application.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(2);
				assertThat(propertySource.getSource().get("username")).isEqualTo("user");
				assertThat(propertySource.getSource().get("password")).isEqualTo("p455w0rd");
			}
			if (propertySource.getName().equals("configmap.stores.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(3);
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(1);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(true);
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("a");
			}
			if (propertySource.getName().equals("secret.stores.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(2);
				assertThat(propertySource.getSource().get("username")).isEqualTo("stores");
				assertThat(propertySource.getSource().get("password")).isEqualTo("password-from-stores");
			}
		});
	}

	@Test
	void testStoresProfileCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);
		Environment environment = environmentRepository.findOne("stores", "dev", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(6);
		environment.getPropertySources().forEach(propertySource -> {
			assertThat(propertySource.getName().equals("configmap.application.default")
					|| propertySource.getName().equals("secret.application.default")
					|| propertySource.getName().equals("configmap.stores.stores-dev.default")
					|| propertySource.getName().equals("secret.stores.default")
					|| propertySource.getName().equals("secret.stores.stores-dev.default")
					|| propertySource.getName().equals("configmap.stores.default"))
				.isTrue();
			if (propertySource.getName().equals("configmap.application.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(3);
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(1);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(true);
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("a");
			}
			else if (propertySource.getName().equals("secret.application.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(2);
				assertThat(propertySource.getSource().get("username")).isEqualTo("user");
				assertThat(propertySource.getSource().get("password")).isEqualTo("p455w0rd");
			}
			else if (propertySource.getName().equals("secret.stores.stores-dev.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(2);
				assertThat(propertySource.getSource().get("username")).isEqualTo("stores-dev");
				assertThat(propertySource.getSource().get("password")).isEqualTo("password-from-stores-dev");
			}
			else if (propertySource.getName().equals("configmap.stores.stores-dev.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(4);
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(2);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(false);
				assertThat(propertySource.getSource().get("dummy.property.string1")).isEqualTo("a");
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("b");
			}
			else if (propertySource.getName().equals("configmap.stores.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(3);
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(1);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(true);
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("a");
			}
			else if (propertySource.getName().equals("secret.stores.default")) {
				assertThat(propertySource.getSource().size()).isEqualTo(2);
				assertThat(propertySource.getSource().get("username")).isEqualTo("stores");
				assertThat(propertySource.getSource().get("password")).isEqualTo("password-from-stores");
			}
			else {
				Assertions.fail("no match in property source names");
			}
		});
	}

	@Test
	void testApplicationPropertiesAnSecretsOverride() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		mockRequests(coreApi);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);
		Environment environment = environmentRepository.findOne("stores-dev", "", "");
		environment.getPropertySources()
			.stream()
			.filter(propertySource -> propertySource.getName().startsWith("configmap"))
			.reduce((first, second) -> second)
			.ifPresent(propertySource -> {
				assertThat(propertySource.getName()).isEqualTo("configmap.application.default");
			});
		environment.getPropertySources()
			.stream()
			.filter(propertySource -> propertySource.getName().startsWith("configmap"))
			.findFirst()
			.ifPresent(propertySource -> {
				assertThat(propertySource.getSource().get("dummy.property.int2")).isEqualTo(2);
				assertThat(propertySource.getSource().get("dummy.property.bool2")).isEqualTo(false);
				assertThat(propertySource.getSource().get("dummy.property.string2")).isEqualTo("b");
			});
		environment.getPropertySources()
			.stream()
			.filter(propertySource -> propertySource.getName().startsWith("secrets"))
			.findFirst()
			.ifPresent(propertySource -> {
				assertThat(propertySource.getSource().get("username")).isEqualTo("stores-dev");
				assertThat(propertySource.getSource().get("password")).isEqualTo("p455w0rd");
			});
	}

	@Test
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
			propertySources.add(new KubernetesClientConfigMapPropertySource(devContext));
			return propertySources;
		});
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi, suppliers,
				"default", PROPERTIES);
		Environment environment = environmentRepository.findOne("stores", "", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
			.isEqualTo("configmap.stores.default.default");

		environment = environmentRepository.findOne("stores", "dev", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("configmap.stores.default.dev");
		assertThat(environment.getPropertySources().get(1).getName())
			.isEqualTo("configmap.stores.default.default");

		environment = environmentRepository.findOne("stores", "dev,prod", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("configmap.stores.default.prod");
		assertThat(environment.getPropertySources().get(0).getSource().get("dummy.property.int2")).isEqualTo(3);
		assertThat(environment.getPropertySources().get(0).getSource().get("dummy.property.bool2")).isEqualTo(true);
		assertThat(environment.getPropertySources().get(0).getSource().get("dummy.property.string2")).isEqualTo("prod");
		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("configmap.stores.default.dev");
		assertThat(environment.getPropertySources().get(1).getSource().get("dummy.property.int2")).isEqualTo(1);
		assertThat(environment.getPropertySources().get(1).getSource().get("dummy.property.bool2")).isEqualTo(false);
		assertThat(environment.getPropertySources().get(1).getSource().get("dummy.property.string2")).isEqualTo("dev");
		assertThat(environment.getPropertySources().get(2).getName())
			.isEqualTo("configmap.stores.default.default");
		assertThat(environment.getPropertySources().get(2).getSource().get("dummy.property.int2")).isEqualTo(1);
		assertThat(environment.getPropertySources().get(2).getSource().get("dummy.property.bool2")).isEqualTo(true);
		assertThat(environment.getPropertySources().get(2).getSource().get("dummy.property.string2")).isEqualTo("a");

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

	private static <T> T yaml(String fileName, Class<T> type) {
		ClassLoader classLoader = KubernetesEnvironmentRepositoryTests.class.getClassLoader();
		String file = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(fileName)))
			.lines().collect(Collectors.joining("\n"));

		return Yaml.loadAs(file, type);

	}

}
