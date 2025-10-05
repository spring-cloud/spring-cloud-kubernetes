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

package org.springframework.cloud.kubernetes.configserver.it;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.kubernetes.configserver.KubernetesConfigServerApplication;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.kubernetes.secrets.enableApi=true", "test.second.config.enabled=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "composite", "kubernetes" })
	class KubernetesCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@Test
		void contextLoads() {

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources().size()).isEqualTo(4);
			assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("configmap.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("secret.gateway.default.default");
		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.config.server.composite[1].type=native",
					"spring.cloud.kubernetes.secrets.enableApi=true",
					"spring.profiles.active=test, composite, kubernetes, native", "test.second.config.enabled=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	class KubernetesSecretsEnabledCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockitoSpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() {

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources().size()).isEqualTo(5);

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
					"spring.cloud.kubernetes.config.enableApi=false", "spring.cloud.kubernetes.secrets.enableApi=true",
					"spring.profiles.active=test ,composite, kubernetes, native", "test.second.config.enabled=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	class KubernetesConfigMapDisabledCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockitoSpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() {

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources().size()).isEqualTo(3);

			assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("secret.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("nativeProperties");

		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.composite[0].type=kubernetes",
					"spring.cloud.config.server.composite[1].type=native",
					"spring.profiles.active=test, composite, kubernetes, native", "test.second.config.enabled=true" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	class KubernetesSecretsDisabledCompositeConfigServerTest {

		@LocalServerPort
		private int port;

		@MockitoSpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() {

			Environment mockNativeEnvironment = new Environment("gateway", "default");
			mockNativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(mockNativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();
			assertThat(environment).isNotNull();
			assertThat(environment.getPropertySources().size()).isEqualTo(3);

			assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("configmap.gateway.default.default");
			assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("nativeProperties");

		}

	}

	@Nested
	@SpringBootTest(classes = { KubernetesConfigServerApplication.class },
			properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.namespace=default",
					"spring.cloud.config.server.native.order=1", "spring.cloud.kubernetes.configserver.order=2",
					"spring.cloud.kubernetes.secrets.enableApi=true", "test.second.config.enabled=true",
					"spring.profiles.active=test, native, kubernetes" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	class NativeAndKubernetesConfigServerTest {

		@LocalServerPort
		private int port;

		@MockitoSpyBean
		private NativeEnvironmentRepository nativeEnvironmentRepository;

		@Test
		void contextLoads() {

			Environment nativeEnvironment = new Environment("gateway", "default");
			nativeEnvironment.add(new PropertySource("nativeProperties", Map.of("key1", "value1")));

			when(nativeEnvironmentRepository.findOne(anyString(), anyString(), eq(null), anyBoolean()))
				.thenReturn(nativeEnvironment);

			ResponseEntity<Environment> response = new RestTemplate().exchange(
					"http://localhost:" + this.port + "/gateway/default", HttpMethod.GET, null, Environment.class);

			Environment environment = response.getBody();

			assertThat(environment.getPropertySources().size()).isEqualTo(3);
			assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("nativeProperties");
			assertThat(environment.getPropertySources().get(1).getName())
				.isEqualTo("configmap.gateway.default.default");
			assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("secret.gateway.default.default");

		}

	}

}
