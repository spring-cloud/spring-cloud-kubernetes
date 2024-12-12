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

import java.time.Duration;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
final class TestAssertions {

	private TestAssertions() {

	}

	/**
	 * assert that 'left' is present, and IFF it is, assert that 'right' is not
	 */
	static void assertReloadLogStatements(String left, String right, CapturedOutput output) {

		await().pollDelay(Duration.ofSeconds(5))
			.atMost(Duration.ofSeconds(15))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> {
				boolean leftIsPresent = output.getOut().contains(left);
				if (leftIsPresent) {
					boolean rightIsPresent = output.getOut().contains(right);
					return !rightIsPresent;
				}
				return false;
			});
	}

	static void replaceConfigMap(KubernetesClient client, ConfigMap configMap, String namespace) {
		client.configMaps().inNamespace(namespace).resource(configMap).update();
	}

	static void replaceSecret(KubernetesClient client, Secret secret, String namespace) {
		client.secrets().inNamespace(namespace).resource(secret).update();
	}

	static void configMap(Phase phase, Util util, ConfigMap configMap, String namespace) {
		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(namespace, configMap, null);
		}
		else {
			util.deleteAndWait(namespace, configMap, null);
		}
	}

	static void secret(Phase phase, Util util, Secret secret, String namespace) {
		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(namespace, null, secret);
		}
		else {
			util.deleteAndWait(namespace, null, secret);
		}
	}

}
