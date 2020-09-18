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

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
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
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class,
		properties = { "spring.application.name=testapp", "spring.cloud.kubernetes.client.namespace=testns",
				"spring.cloud.kubernetes.client.trustCerts=true", "spring.cloud.kubernetes.config.namespace=testns",
				"spring.cloud.kubernetes.secrets.enableApi=true" })
public class CoreTest {

	@ClassRule
	public static KubernetesServer mockServer = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired
	private Environment environment;

	@Autowired(required = false)
	private Config config;

	@Autowired(required = false)
	private KubernetesClient client;

	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = mockServer.getClient();

		mockServer.expect().get().withPath("/api/v1/namespaces/testns/configmaps/testapp")
				.andReturn(200, new ConfigMapBuilder().withData(new HashMap<String, String>() {
					{
						put("spring.kubernetes.test.value", "value1");
					}
				}).build()).always();

		mockServer.expect().get().withPath("/api/v1/namespaces/testns/secrets/testapp")
				.andReturn(200, new SecretBuilder().withData(new HashMap<String, String>() {
					{
						put("amq.user", "YWRtaW4K");
						put("amq.pwd", "MWYyZDFlMmU2N2Rm");
					}
				}).build()).always();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@Test
	public void kubernetesClientConfigBeanShouldBeConfigurableViaSystemProperties() {
		assertThat(config).isNotNull();
		assertThat(config.getMasterUrl()).isEqualTo(mockClient.getConfiguration().getMasterUrl());
		assertThat(config.getNamespace()).isEqualTo("testns");
		assertThat(config.isTrustCerts()).isTrue();
	}

	@Test
	public void kubernetesClientBeanShouldBeConfigurableViaSystemProperties() {
		assertThat(client).isNotNull();
		assertThat(client.getConfiguration().getMasterUrl()).isEqualTo(mockClient.getConfiguration().getMasterUrl());
	}

	@Test
	public void propertiesShouldBeReadFromConfigMap() {
		assertThat(environment.getProperty("spring.kubernetes.test.value")).isEqualTo("value1");
	}

	@Test
	public void propertiesShouldBeReadFromSecret() {
		assertThat(environment.getProperty("amq.user")).isEqualTo("admin");
		assertThat(environment.getProperty("amq.pwd")).isEqualTo("1f2d1e2e67df");
	}

}
