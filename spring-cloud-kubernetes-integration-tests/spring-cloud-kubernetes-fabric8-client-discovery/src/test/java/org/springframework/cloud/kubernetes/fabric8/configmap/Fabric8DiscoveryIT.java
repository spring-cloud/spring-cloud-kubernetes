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

import java.io.FileInputStream;
import java.time.Duration;
import java.util.List;

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
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import org.springframework.cloud.kubernetes.integration.tests.commons.Fabric8Utils;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * @author wind57
 */
public class Fabric8DiscoveryIT {

	private static final String NAMESPACE = "default";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String mockServiceName;

	private static String mockDeploymentName;

	@BeforeAll
	public static void setup() {
		Config config = Config.autoConfigure(null);
		client = new DefaultKubernetesClient(config);

		deployManifests();
		deployMockManifests();
	}

	@AfterAll
	public static void after() {
		deleteManifests();
	}

	@Test
	public void test() {
		WebClient client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
				.baseUrl("localhost/fabric8-discovery/services").build();

		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) client.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(Retry.fixedDelay(15, Duration.ofSeconds(1))
						.filter(x -> ((WebClientResponseException) x).getStatusCode().value() == 503))
				.block();

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

			Deployment deployment = client.apps().deployments().load(getMockDeployment()).get();
			client.apps().deployments().inNamespace(NAMESPACE).create(deployment);
			mockDeploymentName = deployment.getMetadata().getName();

			Service service = client.services().load(getMockService()).get();
			mockServiceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).create(service);

			Fabric8Utils.waitForDeployment(client, "servicea-wiremock-deployment", NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static FileInputStream getService() throws Exception {
		return Fabric8Utils.inputStream("fabric8-discovery-service.yaml");
	}

	private static FileInputStream getDeployment() throws Exception {
		return Fabric8Utils.inputStream("fabric8-discovery-deployment.yaml");
	}

	private static FileInputStream getIngress() throws Exception {
		return Fabric8Utils.inputStream("fabric8-discovery-ingress.yaml");
	}

	private static FileInputStream getMockService() throws Exception {
		return Fabric8Utils.inputStream("fabric8-discovery-wiremock-service.yaml");
	}

	private static FileInputStream getMockDeployment() throws Exception {
		return Fabric8Utils.inputStream("fabric8-discovery-wiremock-deployment.yaml");
	}

}
