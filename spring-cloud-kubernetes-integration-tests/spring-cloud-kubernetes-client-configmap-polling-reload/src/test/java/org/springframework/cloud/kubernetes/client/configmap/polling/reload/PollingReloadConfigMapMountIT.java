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

package org.springframework.cloud.kubernetes.client.configmap.polling.reload;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class PollingReloadConfigMapMountIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-configmap-polling-reload";

	private static final String NAMESPACE = "default";

	private static Util util;

	private static CoreV1Api coreV1Api;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		util = new Util(K3S);
		coreV1Api = new CoreV1Api();
		util.setUp(NAMESPACE);
		manifests(Phase.CREATE);
	}

	@AfterAll
	static void after() throws Exception {
		manifests(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	private static void manifests(Phase phase) {

		V1Deployment deployment = (V1Deployment) util.yaml("deployment-mount.yaml");
		V1Service service = (V1Service) util.yaml("service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("ingress.yaml");
		V1ConfigMap configMap = (V1ConfigMap) util.yaml("configmap-mount.yaml");

		List<V1EnvVar> existing = new ArrayList<>(
				Optional.ofNullable(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
						.orElse(new ArrayList<>()));

		// bootstrap is disabled, which means that in 'application-mount.yaml',
		// config-data support is enabled.
		V1EnvVar mountActiveProfile = new V1EnvVar().name("SPRING_PROFILES_ACTIVE").value("mount");
		V1EnvVar disableBootstrap = new V1EnvVar().name("SPRING_CLOUD_BOOTSTRAP_ENABLED").value("FALSE");

		V1EnvVar debugLevelReloadCommons = new V1EnvVar()
				.name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG_RELOAD").value("DEBUG");
		V1EnvVar debugLevelConfig = new V1EnvVar()
				.name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG").value("DEBUG");
		V1EnvVar debugLevelCommons = new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS")
				.value("DEBUG");

		existing.add(mountActiveProfile);
		existing.add(disableBootstrap);
		existing.add(debugLevelReloadCommons);
		existing.add(debugLevelCommons);
		existing.add(debugLevelConfig);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(existing);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, configMap, null);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, configMap, null);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	@Test
	public void test() {

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(60, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	private String logs() {
		try {
			String appPodName = K3S.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = K3S.execInContainer("sh", "-c", "kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}
