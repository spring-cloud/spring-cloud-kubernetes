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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author mbialkowski1
 */
class Fabric8DiscoveryNamespaceFilterIT {

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_LEFT = "namespace-left";

	private static final String NAMESPACE_RIGHT = "namespace-right";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String mockServiceName;

	private static String mockDeploymentName;

	private static String mockDeploymentImage;

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
		deployMockManifests();
	}

	@AfterAll
	static void after() throws Exception {
		deleteManifests();
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.cleanUpDownloadedImage(mockDeploymentImage);
	}

	@Test
	void test() {
		WebClient clientServices = builder().baseUrl("localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("wiremock"));

		WebClient clientEndpoints = builder().baseUrl("localhost/endpoints/wiremock").build();

		List<Endpoints> endpoints = clientEndpoints.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<Endpoints>>() {
				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(endpoints.size(), 1);
		Assertions.assertEquals(endpoints.get(0).getMetadata().getNamespace(), NAMESPACE_LEFT);

	}

	private static void deleteManifests() {

		try {

			client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).delete();
			client.services().inNamespace(NAMESPACE).withName(serviceName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(ingressName).delete();

			client.services().inNamespace(NAMESPACE_LEFT).withName(mockServiceName).delete();
			client.apps().deployments().inNamespace(NAMESPACE_LEFT).withName(mockDeploymentName).delete();

			client.services().inNamespace(NAMESPACE_RIGHT).withName(mockServiceName).delete();
			client.apps().deployments().inNamespace(NAMESPACE_RIGHT).withName(mockDeploymentName).delete();

			client.rbac().clusterRoleBindings().withName("admin-default").delete();
			client.namespaces().withName(NAMESPACE_LEFT).delete();
			client.namespaces().withName(NAMESPACE_RIGHT).delete();

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
			List<EnvVar> env = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
			env.add(new EnvVar("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0", NAMESPACE_LEFT, null));

			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(env);

			client.apps().deployments().inNamespace(NAMESPACE).resource(deployment).create();
			deploymentName = deployment.getMetadata().getName();

			Service service = client.services().load(getService()).get();
			serviceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).resource(service).create();

			Ingress ingress = client.network().v1().ingresses().load(getIngress()).get();
			ingressName = ingress.getMetadata().getName();
			client.network().v1().ingresses().inNamespace(NAMESPACE).resource(ingress).create();

			client.rbac().clusterRoleBindings().resource(client.rbac().clusterRoleBindings().load(getAdminRole()).get()).create();

			Fabric8Utils.waitForDeployment(client, "spring-cloud-kubernetes-fabric8-client-discovery-deployment",
					NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployMockManifests() {

		try {
			deployInMockInNamespace(NAMESPACE_LEFT);
			deployInMockInNamespace(NAMESPACE_RIGHT);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deployInMockInNamespace(String namespace) throws Exception {

		Namespace namespaceDef = new Namespace();
		ObjectMeta meta = new ObjectMeta();
		meta.setName(namespace);
		meta.setNamespace(namespace);
		namespaceDef.setMetadata(meta);
		client.namespaces().resource(namespaceDef).create();

		Deployment deployment = client.apps().deployments().load(getMockDeployment()).get();
		String[] image = K8SUtils.getImageFromDeployment(deployment).split(":");
		Commons.pullImage(image[0], image[1], K3S);
		Commons.loadImage(image[0], image[1], "wiremock", K3S);
		client.apps().deployments().inNamespace(namespace).resource(deployment).create();
		mockDeploymentName = deployment.getMetadata().getName();
		mockDeploymentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();

		Service service = client.services().load(getMockService()).get();
		mockServiceName = service.getMetadata().getName();
		client.services().inNamespace(namespace).resource(service).create();

		Fabric8Utils.waitForDeployment(client, "wiremock-deployment", namespace, 2, 600);
	}

	private static InputStream getService() {
		return Fabric8Utils.inputStream("fabric8-discovery-service.yaml");
	}

	private static InputStream getDeployment() {
		return Fabric8Utils.inputStream("fabric8-discovery-deployment.yaml");
	}

	private static InputStream getAdminRole() {
		return Fabric8Utils.inputStream("namespace-filter/fabric8-cluster-admin-serviceaccount-role.yaml");
	}

	private static InputStream getIngress() {
		return Fabric8Utils.inputStream("fabric8-discovery-ingress.yaml");
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
		return Retry.fixedDelay(15, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
