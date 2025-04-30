/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.configserver.it;

import java.util.Map;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapsCache;
import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.configserver.KubernetesConfigServerApplication;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Arjav Dongaonkar
 */
class CompositeKubernetesIntegrationTests {

	private static V1ConfigMap buildConfigMap(String name, String namespace) {
		return new V1ConfigMapBuilder()
			.withMetadata(
					new V1ObjectMetaBuilder().withName(name).withNamespace(namespace).build())
			.addToData(Constants.APPLICATION_YAML, "dummy:\n  property:\n    string: \"" + name + "\"\n")
			.build();
	}

	private static V1Secret buildSecret(String name, String namespace) {
		return new V1SecretBuilder()
			.withMetadata(
					new V1ObjectMetaBuilder().withName(name).withNamespace(namespace).build())
			.addToData("password", "p455w0rd".getBytes())
			.addToData("username", "user".getBytes())
			.build();
	}

	private static final V1ConfigMapList CONFIGMAP_DEFAULT_LIST = new V1ConfigMapList()
		.addItemsItem(buildConfigMap("gateway", "default"));

	private static final V1SecretList SECRET_DEFAULT_LIST = new V1SecretListBuilder()
		.addToItems(buildSecret("gateway", "default"))
		.build();

	@AfterEach
	void after() {
		new KubernetesClientConfigMapsCache().discardAll();
	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.config.name=compositeconfigserver",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.kubernetes.secrets.enableApi=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "composite", "kubernetes" })
	class KubernetesCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockBean
		private CoreV1Api coreV1Api;

		@Test
		void contextLoads() throws ApiException {
			when(coreV1Api.listNamespacedConfigMap(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(CONFIGMAP_DEFAULT_LIST);
			when(coreV1Api.listNamespacedSecret(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(SECRET_DEFAULT_LIST);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources()).hasSize(4);
			assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("configmap.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).contains("secret.gateway.default.default");
		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.config.server.composite[1].type=native",
					"spring.cloud.kubernetes.secrets.enableApi=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "composite", "kubernetes", "native" })
	class KubernetesSecretsEnabledCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockBean
		private CoreV1Api coreV1Api;

		@SpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() throws Exception {
			when(coreV1Api.listNamespacedConfigMap(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(CONFIGMAP_DEFAULT_LIST);
			when(coreV1Api.listNamespacedSecret(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(SECRET_DEFAULT_LIST);

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources()).hasSizeGreaterThanOrEqualTo(5);

			assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("configmap.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).contains("secret.gateway.default.default");

			assertThat(environment.getPropertySources()).anyMatch(ps -> ps.getName().contains("native"));
		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.config.server.composite[1].type=native",
					"spring.cloud.kubernetes.config.enableApi=false",
					"spring.cloud.kubernetes.secrets.enableApi=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "composite", "kubernetes", "native" })
	class KubernetesConfigMapDisabledCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockBean
		private CoreV1Api coreV1Api;

		@SpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() throws Exception {
			when(coreV1Api.listNamespacedConfigMap(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(CONFIGMAP_DEFAULT_LIST);
			when(coreV1Api.listNamespacedSecret(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(SECRET_DEFAULT_LIST);

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources()).hasSizeGreaterThanOrEqualTo(3);

			assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("secret.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).contains("nativeProperties");

			assertThat(environment.getPropertySources()).anyMatch(ps -> ps.getName().contains("native"));
		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.config.server.composite[1].type=native" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "composite", "kubernetes", "native" })
	class KubernetesSecretsDisabledCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockBean
		private CoreV1Api coreV1Api;

		@SpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() throws Exception {
			when(coreV1Api.listNamespacedConfigMap(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(CONFIGMAP_DEFAULT_LIST);
			when(coreV1Api.listNamespacedSecret(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(SECRET_DEFAULT_LIST);

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources()).hasSizeGreaterThanOrEqualTo(3);

			assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("configmap.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).contains("nativeProperties");

			assertThat(environment.getPropertySources()).anyMatch(ps -> ps.getName().contains("native"));
		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.config.name:compositeconfigserver", "spring.main.cloud-platform=KUBERNETES",
					"spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.native.order=1", "spring.cloud.kubernetes.configserver.order=2",
					"spring.cloud.kubernetes.secrets.enableApi=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "native", "kubernetes" })
	class NativeAndKubernetesConfigServerTest {

		@LocalServerPort
		private int port;

		@MockBean
		private CoreV1Api coreV1Api;

		@SpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() throws Exception {
			when(coreV1Api.listNamespacedConfigMap(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(CONFIGMAP_DEFAULT_LIST);
			when(coreV1Api.listNamespacedSecret(
				"default", null, null, null, null, null, null, null, null, null, null, null))
				.thenReturn(SECRET_DEFAULT_LIST);

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Mockito.verify(coreV1Api, Mockito.times(1)).listNamespacedConfigMap(
				"default", null, null, null, null, null, null, null, null, null, null, null);

			Mockito.verify(coreV1Api, Mockito.times(1)).listNamespacedSecret(
				"default", null, null, null, null, null, null, null, null, null, null, null);

			Environment environment = response.getBody();

			assertThat(3).isEqualTo(environment.getPropertySources().size());
			assertThat("nativeProperties").isEqualTo(environment.getPropertySources().get(0).getName());
			assertThat(environment.getPropertySources().get(1).getName().contains("configmap.gateway.default.default")
					&& !environment.getPropertySources().get(1).getName().contains("nativeProperties"))
				.isTrue();
			assertThat(environment.getPropertySources().get(2).getName()).contains("secret.gateway.default.default");
		}

	}

}
