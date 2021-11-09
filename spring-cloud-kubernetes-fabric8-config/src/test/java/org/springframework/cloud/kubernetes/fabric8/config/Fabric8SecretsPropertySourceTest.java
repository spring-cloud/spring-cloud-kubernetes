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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.Base64;
import java.util.Collections;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.example.App;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = "spring.main.cloud-platform=KUBERNETES")
@TestPropertySource("classpath:/application-secrets.properties")
@EnableKubernetesMockClient(crud = true, https = false)
public class Fabric8SecretsPropertySourceTest {

	private static final String NAMESPACE = "test";

	private static KubernetesClient mockClient;

	@ClassRule
	public static KubernetesServer mockServer = new KubernetesServer(false);

	private static final String SECRET_VALUE = "secretValue";

	@Autowired
	private Fabric8SecretsPropertySourceLocator propertySourceLocator;

	@Autowired
	private Environment environment;

	@BeforeAll
	public static void setUpBeforeClass() {

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, NAMESPACE);
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Secret secret = new SecretBuilder().withNewMetadata().withName("test-secret")
				.withLabels(singletonMap("foo", "bar")).endMetadata()
				.addToData("secretName", Base64.getEncoder().encodeToString(SECRET_VALUE.getBytes())).build();
		mockClient.secrets().inNamespace(NAMESPACE).create(secret);

		mockServer.before();
	}

	@AfterAll
	public static void tearDown() {
		mockServer.after();
	}

	@Test
	public void toStringShouldNotExposeSecretValues() {
		PropertySource<?> propertySource = this.propertySourceLocator.locate(this.environment);
		assertThat(propertySource.toString()).doesNotContain(SECRET_VALUE);
		assertThat(propertySource.getProperty("secretName")).isEqualTo("secretValue");
	}

	@Test
	public void constructorShouldThrowExceptionOnFailureWhenFailFastIsEnabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatThrownBy(() -> new Fabric8SecretsPropertySource(mockServer.getClient(), name, namespace,
				Collections.emptyMap(), true)).isInstanceOf(IllegalStateException.class)
						.hasMessage("Unable to read Secret with name '" + name + "' or labels [{}] in namespace '"
								+ namespace + "'");
	}

	@Test
	public void constructorShouldNotThrowExceptionOnFailureWhenFailFastIsDisabled() {
		final String name = "my-config";
		final String namespace = "default";
		final String path = String.format("/api/v1/namespaces/%s/secrets/%s", namespace, name);

		mockServer.expect().withPath(path).andReturn(500, "Internal Server Error").once();
		assertThatNoException().isThrownBy(() -> new Fabric8SecretsPropertySource(mockServer.getClient(), name,
				namespace, Collections.emptyMap(), false));
	}

}
