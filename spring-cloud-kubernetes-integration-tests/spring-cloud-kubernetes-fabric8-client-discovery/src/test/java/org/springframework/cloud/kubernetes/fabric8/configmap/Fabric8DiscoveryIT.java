/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.configmap;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
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

/**
 * @author wind57
 */
class Fabric8DiscoveryIT {

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String mockServiceName;

	private static String mockDeploymentName;

	private static final K3sContainer K3S = Commons.container();

	private static Deployment wiremockDeployment;

	private static String wiremockImageName;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		client = new DefaultKubernetesClient(config);
		wiremockDeployment = client.apps().deployments().load(getMockDeployment()).get();
		wiremockImageName = wiremockDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		Commons.validateImage(wiremockImageName, K3S);

		String[] both = wiremockImageName.split(":", 2);
		Commons.loadImage(both[0], both[1], "wiremock", K3S);

		Fabric8Utils.setUp(client, NAMESPACE);

		deployManifests();
		deployMockManifests();
	}

	@AfterAll
	static void after() throws Exception {
		deleteManifests();
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.cleanUpDownloadedImage(wiremockImageName);
	}

	@Test
	void test() {
		WebClient client = builder().baseUrl("localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) client.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(result.size(), 3);
		Assertions.assertTrue(result.contains("kubernetes"));
		Assertions.assertTrue(result.contains("spring-cloud-kubernetes-fabric8-client-discovery"));
		Assertions.assertTrue(result.contains("servicea-wiremock"));
	}

	private static void deleteManifests() {

		try {

			client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).delete();
			client.services().inNamespace(NAMESPACE).withName(serviceName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(ingressName).delete();

			client.services().inNamespace(NAMESPACE).withName(mockServiceName).delete();
			client.apps().deployments().inNamespace(NAMESPACE).withName(mockDeploymentName).delete();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployManifests() {

		try {

			Deployment deployment = client.apps().deployments().load(getDeployment()).get();

			String version = K8SUtils.getPomVersion();
			String currentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(currentImage + ":" + version);

			client.apps().deployments().inNamespace(NAMESPACE).create(deployment);
			deploymentName = deployment.getMetadata().getName();

			Service service = client.services().load(getService()).get();
			serviceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).create(service);

			Ingress ingress = client.network().v1().ingresses().load(getIngress()).get();
			ingressName = ingress.getMetadata().getName();
			client.network().v1().ingresses().inNamespace(NAMESPACE).create(ingress);

			Fabric8Utils.waitForDeployment(client, "spring-cloud-kubernetes-fabric8-client-discovery-deployment",
					NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployMockManifests() {

		try {
			client.apps().deployments().inNamespace(NAMESPACE).create(wiremockDeployment);
			mockDeploymentName = wiremockDeployment.getMetadata().getName();
			Service service = client.services().load(getMockService()).get();
			mockServiceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).create(service);

			Fabric8Utils.waitForDeployment(client, "servicea-wiremock-deployment", NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static InputStream getService() {
		return Fabric8Utils.inputStream("fabric8-discovery-service.yaml");
	}

	private static InputStream getDeployment() {
		return Fabric8Utils.inputStream("fabric8-discovery-deployment.yaml");
	}

	private static InputStream getIngress() {
		return Fabric8Utils.inputStream("fabric8-discovery-ingress.yaml");
	}

	private static InputStream getMockService() {
		return Fabric8Utils.inputStream("wiremock/fabric8-discovery-wiremock-service.yaml");
	}

	private static InputStream getMockDeployment() {
		return Fabric8Utils.inputStream("wiremock/fabric8-discovery-wiremock-deployment.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
