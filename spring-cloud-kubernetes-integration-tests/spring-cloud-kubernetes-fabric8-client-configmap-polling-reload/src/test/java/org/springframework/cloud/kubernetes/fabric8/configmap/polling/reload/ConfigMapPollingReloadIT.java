/*
 * Copyright 2013-2022 the original author or authors.
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
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.internal.HasMetadataOperation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Fabric8Utils;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class ConfigMapPollingReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-polling-reload";

	private static final String NAMESPACE = "default";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String configMapName;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		client = new KubernetesClientBuilder().withConfig(config).build();
		Fabric8Utils.setUp(client, NAMESPACE);
		deployManifests();
	}

	@AfterAll
	static void after() throws Exception {
		deleteManifests();
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@SuppressWarnings({ "raw", "unchecked" })
	@Test
	void test() {
		WebClient webClient = builder().baseUrl("localhost/key").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the configmap
		Assertions.assertEquals("initial", result);

		// then deploy a new version of configmap
		// since we poll and have reload in place, the new property must be visible
		ConfigMap map = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").withName("poll-reload").build())
				.withData(Map.of("application.properties", "from.properties.key=after-change")).build();

		// the weird cast comes from :
		// https://github.com/fabric8io/kubernetes-client/issues/2445
		((HasMetadataOperation) client.configMaps().inNamespace("default").withName("poll-reload")).resource(map)
				.createOrReplace();

		await().timeout(Duration.ofSeconds(60)).until(() -> webClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(String.class).retryWhen(retrySpec()).block().equals("after-change"));

	}

	private static void deleteManifests() {

		try {

			client.configMaps().inNamespace(NAMESPACE).withName(configMapName).delete();
			client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).delete();
			client.services().inNamespace(NAMESPACE).withName(serviceName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(ingressName).delete();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployManifests() {

		try {

			ConfigMap configMap = client.configMaps().load(getConfigMap()).get();
			configMapName = configMap.getMetadata().getName();
			client.configMaps().resource(configMap).create();

			Deployment deployment = client.apps().deployments().load(getDeployment()).get();

			String version = K8SUtils.getPomVersion();
			String currentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(currentImage + ":" + version);

			client.apps().deployments().inNamespace(NAMESPACE).create(deployment);
			deploymentName = deployment.getMetadata().getName();

			Service service = client.services().load(getService()).get();
			serviceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).resource(service).create();

			Ingress ingress = client.network().v1().ingresses().load(getIngress()).get();
			ingressName = ingress.getMetadata().getName();
			client.network().v1().ingresses().inNamespace(NAMESPACE).resource(ingress).create();

			Fabric8Utils.waitForDeployment(client,
					"spring-cloud-kubernetes-fabric8-client-configmap-deployment-polling-reload", NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static InputStream getService() {
		return Fabric8Utils.inputStream("service.yaml");
	}

	private static InputStream getDeployment() {
		return Fabric8Utils.inputStream("deployment.yaml");
	}

	private static InputStream getIngress() {
		return Fabric8Utils.inputStream("ingress.yaml");
	}

	private static InputStream getConfigMap() {
		return Fabric8Utils.inputStream("configmap.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(60, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
