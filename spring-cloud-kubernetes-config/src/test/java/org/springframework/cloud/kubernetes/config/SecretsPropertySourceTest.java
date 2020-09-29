/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config;

import java.util.Base64;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class)
@TestPropertySource("classpath:/application-secrets.properties")
public class SecretsPropertySourceTest {

	private static final String NAMESPACE = "test";

	private static final String SECRET_VALUE = "secretValue";

	@ClassRule
	public static KubernetesServer server = new KubernetesServer(false, true);

	@Autowired
	private SecretsPropertySourceLocator propertySourceLocator;

	@Autowired
	private Environment environment;

	@BeforeClass
	public static void setUpBeforeClass() {
		KubernetesClient mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, NAMESPACE);
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Secret secret = new SecretBuilder().withNewMetadata().withLabels(singletonMap("foo", "bar")).endMetadata()
				.addToData("secretName", Base64.getEncoder().encodeToString(SECRET_VALUE.getBytes())).build();
		mockClient.secrets().inNamespace(NAMESPACE).create(secret);
	}

	@Test
	public void toStringShouldNotExposeSecretValues() {
		String actual = this.propertySourceLocator.locate(this.environment).toString();

		assertThat(actual).doesNotContain(SECRET_VALUE);
	}

}
