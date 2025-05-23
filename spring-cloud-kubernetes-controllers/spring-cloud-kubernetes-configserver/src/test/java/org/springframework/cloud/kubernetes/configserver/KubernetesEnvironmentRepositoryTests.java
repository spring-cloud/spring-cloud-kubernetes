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

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SecretListBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigContext;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySource;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapsCache;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsCache;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySource;
import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.commons.config.NamedConfigMapNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;
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

	private static final String VALUE = "dummy:\n  property:\n    string2: \"a\"\n    int2: 1\n    bool2: true\n";

	private static final String DEFAULT_NAMESPACE = "default";

	private static final V1ConfigMapList CONFIGMAP_ONE_LIST = new V1ConfigMapList()
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("storessingle").withNamespace(DEFAULT_NAMESPACE).build())
			.addToData("storessingle.yaml", VALUE)
			.addToData("storessingle-dev.yaml",
					"dummy:\n  property:\n    string2: \"dev\"\n    int2: 1\n    bool2: false\n")
			.addToData("storessingle-qa.yaml",
					"dummy:\n  property:\n    string2: \"qa\"\n    int2: 2\n    bool2: true\n")
			.addToData("storessingle-prod.yaml",
					"dummy:\n  property:\n    string2: \"prod\"\n    int2: 3\n    bool2: true\n")
			.build());

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST = new V1ConfigMapList()
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("application").withNamespace(DEFAULT_NAMESPACE).build())
			.addToData(Constants.APPLICATION_YAML, VALUE)
			.build())
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("stores").withNamespace(DEFAULT_NAMESPACE).build())
			.addToData(Constants.APPLICATION_YAML, VALUE)
			.build())
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("stores-dev").withNamespace(DEFAULT_NAMESPACE).build())
			.addToData(Constants.APPLICATION_YAML,
					"dummy:\n  property:\n    string1: \"a\"\n    string2: \"b\"\n    int2: 2\n    bool2: false\n")
			.build());

	private static final V1ConfigMapList CONFIGMAP_DEV_LIST = new V1ConfigMapList()
		.addItemsItem(new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("stores").withNamespace("dev").build())
			.addToData(Constants.APPLICATION_YAML,
					"dummy:\n  property:\n    string2: \"dev\"\n    int2: 1\n    bool2: true\n")
			.build());

	private static final V1SecretList SECRET_LIST = new V1SecretListBuilder()
		.addToItems(new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("application").withNamespace("default").build())
			.addToData("password", "p455w0rd".getBytes())
			.addToData("username", "user".getBytes())
			.build())
		.addToItems(new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("stores").withNamespace("default").build())
			.addToData("password", "password-from-stores".getBytes())
			.addToData("username", "stores".getBytes())
			.build())
		.addToItems(new V1SecretBuilder()
			.withMetadata(new V1ObjectMetaBuilder().withName("stores-dev").withNamespace("default").build())
			.addToData("password", "password-from-stores-dev".getBytes())
			.addToData("username", "stores-dev".getBytes())
			.build())
		.build();

	private static final KubernetesConfigServerProperties PROPERTIES = properties();

	@BeforeAll
	static void before() {
		KUBERNETES_PROPERTY_SOURCE_SUPPLIER.add((coreApi, applicationName, namespace, springEnv) -> {
			List<MapPropertySource> propertySources = new ArrayList<>();

			NormalizedSource defaultSource = new NamedConfigMapNormalizedSource(applicationName, "default", false,
					true);
			KubernetesClientConfigContext defaultContext = new KubernetesClientConfigContext(coreApi, defaultSource,
					"default", springEnv);
			propertySources.add(new KubernetesClientConfigMapPropertySource(defaultContext));

			if ("stores".equals(applicationName) && "dev".equals(namespace)) {
				NormalizedSource devSource = new NamedConfigMapNormalizedSource(applicationName, "dev", false, true);
				KubernetesClientConfigContext devContext = new KubernetesClientConfigContext(coreApi, devSource, "dev",
						springEnv);
				propertySources.add(new KubernetesClientConfigMapPropertySource(devContext));
			}
			return propertySources;
		});
		KUBERNETES_PROPERTY_SOURCE_SUPPLIER.add((coreApi, applicationName, namespace, springEnv) -> {
			List<MapPropertySource> propertySources = new ArrayList<>();

			NormalizedSource source = new NamedSecretNormalizedSource(applicationName, "default", false, true);
			KubernetesClientConfigContext context = new KubernetesClientConfigContext(coreApi, source, "default",
					springEnv);

			propertySources.add(new KubernetesClientSecretsPropertySource(context));
			return propertySources;
		});
	}

	@AfterEach
	void after() {
		new KubernetesClientConfigMapsCache().discardAll();
		new KubernetesClientSecretsCache().discardAll();
	}

	@Test
	public void testApplicationCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(SECRET_LIST);
		when(coreApi.listNamespacedConfigMap(eq("dev"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEV_LIST);
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi,
				KUBERNETES_PROPERTY_SOURCE_SUPPLIER, "default", PROPERTIES);
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
	}

	@Test
	public void testApplicationCaseWithNewConstructor() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		KubernetesConfigServerProperties properties = mock(KubernetesConfigServerProperties.class);
		when(properties.getOrder()).thenReturn(0);

		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(SECRET_LIST);
		when(coreApi.listNamespacedConfigMap(eq("dev"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEV_LIST);

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
	public void testStoresCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(coreApi.listNamespacedConfigMap(eq("dev"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEV_LIST);
		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(SECRET_LIST);
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
	public void testStoresProfileCase() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(SECRET_LIST);
		when(coreApi.listNamespacedConfigMap(eq("dev"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEV_LIST);
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
	public void testApplicationPropertiesAnSecretsOverride() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEFAULT_LIST);
		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(SECRET_LIST);
		when(coreApi.listNamespacedConfigMap(eq("dev"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_DEV_LIST);
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
	public void testSingleConfigMapMultipleSources() throws ApiException {
		CoreV1Api coreApi = mock(CoreV1Api.class);
		when(coreApi.listNamespacedConfigMap(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(CONFIGMAP_ONE_LIST);
		when(coreApi.listNamespacedSecret(eq("default"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
				eq(null), eq(null), eq(null), eq(null), eq(null)))
			.thenReturn(new V1SecretList());
		List<KubernetesPropertySourceSupplier> suppliers = new ArrayList<>();
		suppliers.add((coreV1Api, name, namespace, environment) -> {
			List<MapPropertySource> propertySources = new ArrayList<>();
			NormalizedSource devSource = new NamedConfigMapNormalizedSource(name, namespace, false,
					ConfigUtils.Prefix.DEFAULT, true, true);
			KubernetesClientConfigContext devContext = new KubernetesClientConfigContext(coreApi, devSource, "default",
					environment);
			propertySources.add(new KubernetesClientConfigMapPropertySource(devContext));
			return propertySources;
		});
		KubernetesEnvironmentRepository environmentRepository = new KubernetesEnvironmentRepository(coreApi, suppliers,
				"default", PROPERTIES);
		Environment environment = environmentRepository.findOne("storessingle", "", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
			.isEqualTo("configmap.storessingle.default.default");

		environment = environmentRepository.findOne("storessingle", "dev", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("configmap.storessingle.default.dev");
		assertThat(environment.getPropertySources().get(1).getName())
			.isEqualTo("configmap.storessingle.default.default");

		environment = environmentRepository.findOne("storessingle", "dev,prod", "");
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("configmap.storessingle.default.prod");
		assertThat(environment.getPropertySources().get(0).getSource().get("dummy.property.int2")).isEqualTo(3);
		assertThat(environment.getPropertySources().get(0).getSource().get("dummy.property.bool2")).isEqualTo(true);
		assertThat(environment.getPropertySources().get(0).getSource().get("dummy.property.string2")).isEqualTo("prod");
		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("configmap.storessingle.default.dev");
		assertThat(environment.getPropertySources().get(1).getSource().get("dummy.property.int2")).isEqualTo(1);
		assertThat(environment.getPropertySources().get(1).getSource().get("dummy.property.bool2")).isEqualTo(false);
		assertThat(environment.getPropertySources().get(1).getSource().get("dummy.property.string2")).isEqualTo("dev");
		assertThat(environment.getPropertySources().get(2).getName())
			.isEqualTo("configmap.storessingle.default.default");
		assertThat(environment.getPropertySources().get(2).getSource().get("dummy.property.int2")).isEqualTo(1);
		assertThat(environment.getPropertySources().get(2).getSource().get("dummy.property.bool2")).isEqualTo(true);
		assertThat(environment.getPropertySources().get(2).getSource().get("dummy.property.string2")).isEqualTo("a");

	}

	private static KubernetesConfigServerProperties properties() {
		KubernetesConfigServerProperties properties = new KubernetesConfigServerProperties();
		properties.setOrder(1);
		return properties;
	}

}
