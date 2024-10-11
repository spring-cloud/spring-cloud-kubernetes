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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.assertReloadLogStatements;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.replaceConfigMap;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes",
	"logging.level.org.springframework.cloud.kubernetes.fabric8.config.reload=debug",
	"spring.cloud.bootstrap.enabled=true", "spring.cloud.kubernetes.client.namespace=default" })
@ActiveProfiles("one")
class Fabric8EventReloadInformFromOneNamespaceEventNotTriggeredIT extends Fabric8EventReloadBase {

	private static ConfigMap leftConfigMap;

	private static ConfigMap rightConfigMap;

	@Autowired
	private Environment environment;

	@Autowired
	private KubernetesClient kubernetesClient;

	@BeforeAll
	static void beforeAllLocal() {
		InputStream leftConfigMapStream = util.inputStream("manifests/left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("manifests/right-configmap.yaml");
		leftConfigMap = Serialization.unmarshal(leftConfigMapStream, ConfigMap.class);
		rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);

		util.createNamespace("left");
		util.createNamespace("right");

		leftAndRightConfigMap(Phase.CREATE);
	}

	@AfterAll
	static void afterAllLocal() {
		leftAndRightConfigMap(Phase.DELETE);

		util.deleteNamespace("left");
		util.deleteNamespace("right");
	}

	/**
	 * <pre>
	 *     - there are two namespaces : left and right
	 *     - each of the namespaces has one configmap
	 *     - we watch the "left" namespace, but make a change in the configmap in the "right" namespace
	 *     - as such, no event is triggered and "left-configmap" stays as-is
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) throws Exception {
		assertReloadLogStatements("added configmap informer for namespace",
			"added secret informer for namespace", output);
		String currentLeftValue = environment.getProperty("left.value");
		assertThat(currentLeftValue).isEqualTo("left-initial");

		String currentRightValue = environment.getProperty("right.value");
		assertThat(currentRightValue).isEqualTo("right-initial");

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
			.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
			.withData(Map.of("right.value", "right-after-change"))
			.build();

		replaceConfigMap(kubernetesClient, rightConfigMapAfterChange, "right");

		TimeUnit.SECONDS.sleep(10);

//		await().pollThread(run -> {
//				Thread t = new Thread(run);
//				return t;
//			}).atMost(Duration.ofSeconds(30))
//			.pollInterval(Duration.ofSeconds(1))
//			.until(() -> environment.getProperty("right.value").equals("right-after-change"));

		System.out.println("yes");

	}

	private static void leftAndRightConfigMap(Phase phase) {
		if (phase.equals(Phase.CREATE)) {
			util.createAndWait("left", leftConfigMap, null);
			util.createAndWait("right", rightConfigMap, null);
		}
		else {
			util.deleteAndWait("left", leftConfigMap, null);
			util.deleteAndWait("right", rightConfigMap, null);
		}
	}

	@TestConfiguration
	public static class TestConfig {

		@Bean
		@Primary
		KubernetesClient kubernetesClient() {
			String kubeConfigYaml = K3S.getKubeConfigYaml();
			Config config = Config.fromKubeconfig(kubeConfigYaml);
			return new KubernetesClientBuilder().withConfig(config).build();
		}

	}

}
