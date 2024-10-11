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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.boot.test.system.CapturedOutput;

import java.time.Duration;

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
		client.configMaps().inNamespace(namespace).resource(configMap).createOrReplace();
	}

}
