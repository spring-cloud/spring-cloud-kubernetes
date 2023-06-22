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

package org.springframework.cloud.kubernetes.fabric8.configmap.polling.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class PollingReloadConfigMapMountIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-polling-reload";

	private static final String NAMESPACE = "default";

	private static Util util;

	private static KubernetesClient client;

	private static final K3sContainer K3S = Commons.container();

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
	static void after() throws Exception {
		manifests(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	/**
	 * <pre>
	 *     - we have "spring.config.import: kubernetes", which means we will 'locate' property sources
	 *       from config maps.
	 *     - the property above means that at the moment we will be searching for config maps that only
	 *       match the application name, in this specific test there is no such config map.
	 *     - what we will also read, is 'spring.cloud.kubernetes.config.paths', which we have set to
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
		String logs = logs();
		// (1)
		Assertions.assertTrue(logs.contains("paths property sources : [/tmp/application.properties]"));
		// (2)
		Assertions.assertTrue(logs.contains("will add file-based property source : /tmp/application.properties"));
		// (3)
		WebClient webClient = builder().baseUrl("http://localhost/key").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the configmap
		Assertions.assertEquals("as-mount-initial", result);

		// replace data in configmap and wait for k8s to pick it up
		// our polling will detect that and restart the app
		InputStream configMapStream = util.inputStream("mount/configmap-mount.yaml");
		ConfigMap configMap = client.configMaps().load(configMapStream).item();
		configMap.setData(Map.of("application.properties", "from.properties.key=as-mount-changed"));
		client.configMaps().inNamespace("default").resource(configMap).createOrReplace();

		await().timeout(Duration.ofSeconds(360)).until(() -> webClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(String.class).retryWhen(retrySpec()).block().equals("as-mount-changed"));

	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("mount/deployment-mount.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream configMapStream = util.inputStream("mount/configmap-mount.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).item();
		Service service = client.services().load(serviceStream).item();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).item();
		ConfigMap configMap = client.configMaps().load(configMapStream).item();

		List<EnvVar> existing = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());

		// bootstrap is disabled, which means that in 'application-mount.yaml',
		// config-data support is enabled.
		EnvVar mountActiveProfile = new EnvVarBuilder().withName("SPRING_PROFILES_ACTIVE").withValue("mount").build();
		EnvVar disableBootstrap = new EnvVarBuilder().withName("SPRING_CLOUD_BOOTSTRAP_ENABLED").withValue("FALSE")
				.build();

		EnvVar debugLevelReloadCommons = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG_RELOAD").withValue("DEBUG")
				.build();
		EnvVar debugLevelConfig = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG").withValue("DEBUG")
				.build();
		EnvVar debugLevelCommons = new EnvVarBuilder()
				.withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS").withValue("DEBUG").build();

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
