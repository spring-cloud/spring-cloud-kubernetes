/*
 * Copyright 2013-2023 the original author or authors.
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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.commons.config.Constants;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.builder;
import static org.springframework.cloud.kubernetes.fabric8.client.reload.it.TestAssertions.retrySpec;

/**
 * @author wind57
 */
class BootstrapEnabledPollingReloadConfigMapMountIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-reload";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static KubernetesClient client;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();
		util.setUp(NAMESPACE);
		manifests(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
		manifests(Phase.DELETE);
	}

	/**
	 * <pre>
	 *     - we have bootstrap enabled, which means we will 'locate' property sources
	 *       from config maps.
	 *     - there are no explicit config maps to search for, but what we will also read,
	 *     	 is 'spring.cloud.kubernetes.config.paths', which we have set to
	 *     	 '/tmp/application.properties'
	 *       in this test. That is populated by the volumeMounts (see deployment-mount.yaml)
	 *     - we first assert that we are actually reading the path based source via (1), (2) and (3).
	 *
	 *     - we then change the config map content, wait for k8s to pick it up and replace them
	 *     - our polling will then detect that change, and trigger a reload.
	 * </pre>
	 */
	@Test
	void test() {
		// (1)
		Commons.waitForLogStatement("paths property sources : [/tmp/application.properties]", K3S, IMAGE_NAME);
		// (2)
		Commons.waitForLogStatement("will add file-based property source : /tmp/application.properties", K3S,
				IMAGE_NAME);
		// (3)
		WebClient webClient = builder().baseUrl("http://localhost/key").build();
		String result = webClient.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		// we first read the initial value from the configmap
		Assertions.assertEquals("as-mount-initial", result);

		// replace data in configmap and wait for k8s to pick it up
		// our polling will detect that and restart the app
		InputStream configMapStream = util.inputStream("configmap.yaml");
		ConfigMap configMap = Serialization.unmarshal(configMapStream, ConfigMap.class);
		configMap.setData(Map.of(Constants.APPLICATION_PROPERTIES, "from.properties.key=as-mount-changed"));
		client.configMaps().inNamespace("default").resource(configMap).createOrReplace();

		await().timeout(Duration.ofSeconds(360))
			.until(() -> webClient.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(String.class)
				.retryWhen(retrySpec())
				.block()
				.equals("as-mount-changed"));
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("manifests/deployment.yaml");
		InputStream serviceStream = util.inputStream("manifests/service.yaml");
		InputStream ingressStream = util.inputStream("manifests/ingress.yaml");
		InputStream configMapAsStream = util.inputStream("manifests/configmap.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);
		ConfigMap configMap = Serialization.unmarshal(configMapAsStream, ConfigMap.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, configMap, null);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, configMap, null);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

}
