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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestApplication.class,
		properties = { "spring.application.name=testapp", "spring.cloud.kubernetes.client.namespace=testns",
				"spring.cloud.kubernetes.client.trustCerts=true", "spring.cloud.kubernetes.config.namespace=testns",
				"spring.cloud.kubernetes.secrets.enableApi=true" })
@EnableKubernetesMockClient(crud = true, https = false)
public class CoreTest {

	private static KubernetesClient mockClient;

	@Autowired
	private Environment environment;

	@Autowired
	private Config config;

	// not a fan of changing the type from KubernetesClient, but because of:
	// https://github.com/fabric8io/kubernetes-client/issues/3145
	// there is no way to do it otherwise at this time. When that is fixed, I will fix
	// this also
	@Autowired
	private DefaultKubernetesClient client;

	@BeforeAll
	public static void setUpBeforeClass() {

		Map<String, String> data1 = new HashMap<>();
		data1.put("spring.kubernetes.test.value", "value1");
		mockClient.configMaps().inNamespace("testns").createNew().withNewMetadata().withName("testapp").endMetadata()
				.addToData(data1).done();

		Map<String, String> data2 = new HashMap<>();
		data2.put("amq.user", "YWRtaW4K");
		data2.put("amq.pwd", "MWYyZDFlMmU2N2Rm");
		mockClient.secrets().inNamespace("testns").createNew().withNewMetadata().withName("testapp").endMetadata()
				.addToData(data2).done();

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
