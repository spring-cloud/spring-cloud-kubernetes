/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.loadbalancer.it;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
public class Fabric8ClientLoadbalancerIT {

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-loadbalancer";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String mockServiceName;

	private static String mockDeploymentName;

	private static String mockIngressName;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		client = new DefaultKubernetesClient(config);
		Fabric8Utils.setUp(client, NAMESPACE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@BeforeEach
	void beforeEach() {
		deployMockManifests();
	}

	@AfterEach
	void after() {
		deleteManifests();
	}

	@Test
	void testLoadBalancerServiceMode() {

		deployServiceManifests();

		WebClient client = builder().baseUrl("localhost/loadbalancer-it/servicea").build();

		@SuppressWarnings("unchecked")
		Map<String, String> mapResult = (Map<String, String>) client.method(HttpMethod.GET).retrieve()
				.bodyToMono(Map.class).retryWhen(retrySpec()).block();

		assertThat(mapResult.containsKey("mappings")).isTrue();
		assertThat(mapResult.containsKey("meta")).isTrue();

	}

	@Test
	public void testLoadBalancerPodMode() {

		deployPodManifests();

		WebClient client = builder().baseUrl("localhost/loadbalancer-it/servicea").build();

		@SuppressWarnings("unchecked")
		Map<String, String> mapResult = (Map<String, String>) client.method(HttpMethod.GET).retrieve()
				.bodyToMono(Map.class).retryWhen(retrySpec()).block();

		assertThat(mapResult.containsKey("mappings")).isTrue();
		assertThat(mapResult.containsKey("meta")).isTrue();

	}

	private static void deleteManifests() {

		try {

			client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).delete();
			client.services().inNamespace(NAMESPACE).withName(serviceName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(ingressName).delete();

			client.services().inNamespace(NAMESPACE).withName(mockServiceName).delete();
			client.apps().deployments().inNamespace(NAMESPACE).withName(mockDeploymentName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(mockIngressName).delete();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployServiceManifests() {

		try {

			Deployment deployment = client.apps().deployments().load(getServiceDeployment()).get();

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

			Fabric8Utils.waitForDeployment(client, "spring-cloud-kubernetes-fabric8-client-loadbalancer-deployment",
					NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployPodManifests() {

		try {

			Deployment deployment = client.apps().deployments().load(getPodDeployment()).get();

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

			Fabric8Utils.waitForDeployment(client, "spring-cloud-kubernetes-fabric8-client-loadbalancer-deployment",
					NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployMockManifests() {

		try {

			Deployment deployment = client.apps().deployments().load(getMockDeployment()).get();
			String[] image = K8SUtils.getImageFromDeployment(deployment).split(":");
			Commons.pullImage(image[0], image[1], K3S);
			Commons.loadImage(image[0], image[1], "wiremock", K3S);
			client.apps().deployments().inNamespace(NAMESPACE).create(deployment);
			mockDeploymentName = deployment.getMetadata().getName();

			Service service = client.services().load(getMockService()).get();
			mockServiceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).create(service);

			Ingress ingress = client.network().v1().ingresses().load(getMockIngress()).get();
			mockIngressName = ingress.getMetadata().getName();

			Fabric8Utils.waitForDeployment(client, "servicea-wiremock-deployment", NAMESPACE, 2, 600);
			Fabric8Utils.waitForEndpoint(client, "servicea-wiremock", NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static InputStream getIngress() {
		return Fabric8Utils.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-ingress.yaml");
	}

	private static InputStream getService() {
		return Fabric8Utils.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-service.yaml");
	}

	private static InputStream getPodDeployment() {
		return Fabric8Utils.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-pod-deployment.yaml");
	}

	private static InputStream getServiceDeployment() {
		return Fabric8Utils.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-service-deployment.yaml");
	}

	private static InputStream getMockIngress() {
		return Fabric8Utils.inputStream("wiremock/wiremock-ingress.yaml");
	}

	private static InputStream getMockService() {
		return Fabric8Utils.inputStream("wiremock/wiremock-service.yaml");
	}

	private static InputStream getMockDeployment() {
		return Fabric8Utils.inputStream("wiremock/wiremock-deployment.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
