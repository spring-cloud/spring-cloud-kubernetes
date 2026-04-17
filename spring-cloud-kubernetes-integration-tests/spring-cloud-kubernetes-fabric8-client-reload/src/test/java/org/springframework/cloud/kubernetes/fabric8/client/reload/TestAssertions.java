/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.reload;

import java.io.InputStream;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Fabric8KubernetesFixture;

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

		Awaitilities.awaitUntil(20, 1000, () -> {
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

	static void configMap(Phase phase, Fabric8KubernetesFixture fabric8KubernetesFixture, ConfigMap configMap, String namespace) {
		if (phase.equals(Phase.CREATE)) {
			fabric8KubernetesFixture.createAndWait(namespace, configMap, null);
		}
		else {
			fabric8KubernetesFixture.deleteAndWait(namespace, configMap, null);
		}
	}

	static void secret(Phase phase, Fabric8KubernetesFixture fabric8KubernetesFixture, Secret secret, String namespace) {
		if (phase.equals(Phase.CREATE)) {
			fabric8KubernetesFixture.createAndWait(namespace, null, secret);
		}
		else {
			fabric8KubernetesFixture.deleteAndWait(namespace, null, secret);
		}
	}

	static void manifests(Phase phase, Fabric8KubernetesFixture fabric8KubernetesFixture, String namespace) {

		InputStream deploymentStream = fabric8KubernetesFixture.inputStream("manifests/deployment.yaml");
		InputStream serviceStream = fabric8KubernetesFixture.inputStream("manifests/service.yaml");
		InputStream configMapAsStream = fabric8KubernetesFixture.inputStream("manifests/configmap-configtree.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		Service service = Serialization.unmarshal(serviceStream, Service.class);
		ConfigMap configMap = Serialization.unmarshal(configMapAsStream, ConfigMap.class);

		if (phase.equals(Phase.CREATE)) {
			fabric8KubernetesFixture.createAndWait(namespace, configMap, null);
			fabric8KubernetesFixture.createAndWait(namespace, null, deployment, service, true);
		}
		else {
			fabric8KubernetesFixture.deleteAndWait(namespace, configMap, null);
			fabric8KubernetesFixture.deleteAndWait(namespace, deployment, service);
		}

	}

}
