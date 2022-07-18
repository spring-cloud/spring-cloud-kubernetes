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

package org.springframework.cloud.kubernetes.fabric8.istio;

import java.io.File;
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

import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.processExecResult;

/**
 * @author wind57
 */
class Fabric8IstioIT {

	private static final String NAMESPACE = "istio-test";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-istio-it";

	private static final String ISTIO_PROXY = "istio/proxyv2";

	private static final String ISTIO_PILOT = "istio/pilot";

	private static final String ISTIO_VERSION = "1.13.3";

	private static final String LOCAL_ISTIO_BIN_PATH = "../../istio-cli/istio-" + ISTIO_VERSION + "/bin";

	private static final String CONTAINER_ISTIO_BIN_PATH = "/tmp/istio/istio-bin/bin/";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static K3sContainer K3S;

	@BeforeAll
	static void beforeAll() throws Exception {
		// Path passed to K3S container must be absolute
		String absolutePath = new File(LOCAL_ISTIO_BIN_PATH).getAbsolutePath();
		K3S = Commons.container().withFileSystemBind(absolutePath, CONTAINER_ISTIO_BIN_PATH);
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Commons.pullImage(ISTIO_PROXY, ISTIO_VERSION, K3S);
		Commons.loadImage(ISTIO_PROXY, ISTIO_VERSION, "istioproxy", K3S);
		Commons.pullImage(ISTIO_PILOT, ISTIO_VERSION, K3S);
		Commons.loadImage(ISTIO_PILOT, ISTIO_VERSION, "istiopilot", K3S);

		processExecResult(K3S.execInContainer("sh", "-c", "kubectl create namespace istio-test"));
		processExecResult(
				K3S.execInContainer("sh", "-c", "kubectl label namespace istio-test istio-injection=enabled"));

		// for Mac M1 with aarch64
		if (System.getProperty("os.arch").equals("aarch64")) {
			processExecResult(K3S.execInContainer("sh", "-c", CONTAINER_ISTIO_BIN_PATH + "istioctl"
					+ " --kubeconfig=/etc/rancher/k3s/k3s.yaml install --set hub=docker.io/querycapistio --set profile=minimal -y"));
		}
		else {
			processExecResult(K3S.execInContainer("sh", "-c", CONTAINER_ISTIO_BIN_PATH + "istioctl"
					+ " --kubeconfig=/etc/rancher/k3s/k3s.yaml install --set profile=minimal -y"));
		}

		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		client = new DefaultKubernetesClient(config);
		Fabric8Utils.setUpIstio(client, NAMESPACE);

		deployManifests();
	}

	@AfterAll
	static void afterAll() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@AfterAll
	static void after() {
		deleteManifests();
	}

	@Test
	void test() {
		WebClient client = builder().baseUrl("localhost/profiles").build();

		@SuppressWarnings("unchecked")
		List<String> result = client.method(HttpMethod.GET).retrieve().bodyToMono(List.class).retryWhen(retrySpec())
				.block();

		// istio profile is present
		Assertions.assertTrue(result.contains("istio"));
	}

	private static void deleteManifests() {

		try {

			client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).delete();
			client.services().inNamespace(NAMESPACE).withName(serviceName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(ingressName).delete();

		}
		catch (Exception e) {
			e.printStackTrace();
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

			Fabric8Utils.waitForIngress(client, ingressName, NAMESPACE);
			Fabric8Utils.waitForDeployment(client, "spring-cloud-kubernetes-fabric8-istio-it-deployment", NAMESPACE, 2,
					600);

		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	private static InputStream getService() {
		return Fabric8Utils.inputStream("istio-service.yaml");
	}

	private static InputStream getDeployment() {
		return Fabric8Utils.inputStream("istio-deployment.yaml");
	}

	private static InputStream getIngress() {
		return Fabric8Utils.inputStream("istio-ingress.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
