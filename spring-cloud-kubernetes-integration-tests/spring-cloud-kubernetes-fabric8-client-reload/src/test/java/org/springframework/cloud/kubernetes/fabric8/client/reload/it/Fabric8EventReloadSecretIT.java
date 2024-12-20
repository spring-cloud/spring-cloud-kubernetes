/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.reload.it;

import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.fabric8.client.reload.SecretProperties;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.assertReloadLogStatements;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.replaceSecret;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.secret;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
		"logging.level.org.springframework.cloud.kubernetes.fabric8.config.reload=debug",
		"spring.cloud.kubernetes.client.namespace=default" })
@ActiveProfiles("with-secret")
class Fabric8EventReloadSecretIT extends Fabric8EventReloadBase {

	private static final String NAMESPACE = "default";

	private static Secret secret;

	@Autowired
	private KubernetesClient kubernetesClient;

	@Autowired
	private SecretProperties secretProperties;

	@BeforeAll
	static void beforeAllLocal() {

		// set system properties very early, so that when
		// 'Fabric8ConfigDataLocationResolver'
		// loads KubernetesClient from Config, these would be already present
		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		String caCertData = config.getCaCertData();
		String clientCertData = config.getClientCertData();
		String clientKeyData = config.getClientKeyData();
		String clientKeyAlgo = config.getClientKeyAlgo();
		String clientKeyPass = config.getClientKeyPassphrase();
		String masterUrl = new KubernetesClientBuilder().withConfig(config).build().getConfiguration().getMasterUrl();

		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, masterUrl);
		System.setProperty(Config.KUBERNETES_CA_CERTIFICATE_DATA_SYSTEM_PROPERTY, caCertData);
		System.setProperty(Config.KUBERNETES_CLIENT_CERTIFICATE_DATA_SYSTEM_PROPERTY, clientCertData);
		System.setProperty(Config.KUBERNETES_CLIENT_KEY_DATA_SYSTEM_PROPERTY, clientKeyData);
		System.setProperty(Config.KUBERNETES_CLIENT_KEY_ALGO_SYSTEM_PROPERTY, clientKeyAlgo);
		System.setProperty(Config.KUBERNETES_CLIENT_KEY_PASSPHRASE_SYSTEM_PROPERTY, clientKeyPass);

		InputStream secretStream = util.inputStream("manifests/secret.yaml");
		secret = Serialization.unmarshal(secretStream, Secret.class);
		secret(Phase.CREATE, util, secret, NAMESPACE);
	}

	@AfterAll
	static void afterAllLocal() {
		secret(Phase.DELETE, util, secret, NAMESPACE);
	}

	/**
	 * <pre>
	 *     - secret with no labels and data: from.secret.properties.key = secret-initial exists in namespace default
	 *
	 *     - then we change the secret by adding a label, this in turn does not
	 *       change the result
	 *
	 *     - then we change data inside the secret, and we must see the updated value.
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {
		assertReloadLogStatements("added secret informer for namespace", "added configmap informer for namespace",
				output);
		assertThat(secretProperties.getKey()).isEqualTo("secret-initial");

		Secret secret = new SecretBuilder()
			.withMetadata(new ObjectMetaBuilder().withLabels(Map.of("letter", "a"))
				.withNamespace("default")
				.withName("event-reload")
				.build())
			.withData(Map.of(Constants.APPLICATION_PROPERTIES,
					Base64.getEncoder().encodeToString("from.secret.properties.key=secret-initial".getBytes())))
			.build();
		replaceSecret(kubernetesClient, secret, NAMESPACE);

		await().atMost(Duration.ofSeconds(60))
			.pollDelay(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("Secret event-reload was updated in namespace default"));

		await().atMost(Duration.ofSeconds(60))
			.pollDelay(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("data in secret has not changed, will not reload"));
		assertThat(secretProperties.getKey()).isEqualTo("secret-initial");

		// change data
		secret = new SecretBuilder()
			.withMetadata(new ObjectMetaBuilder().withNamespace("default").withName("event-reload").build())
			.withData(Map.of(Constants.APPLICATION_PROPERTIES,
					Base64.getEncoder().encodeToString("from.secret.properties.key=secret-initial-changed".getBytes())))
			.build();
		replaceSecret(kubernetesClient, secret, NAMESPACE);

		await().atMost(Duration.ofSeconds(60))
			.pollDelay(Duration.ofSeconds(1))
			.until(() -> secretProperties.getKey().equals("secret-initial-changed"));
	}

}
