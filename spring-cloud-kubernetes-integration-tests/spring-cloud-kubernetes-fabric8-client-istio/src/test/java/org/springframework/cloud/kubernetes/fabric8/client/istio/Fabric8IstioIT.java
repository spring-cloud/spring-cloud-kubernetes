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

package org.springframework.cloud.kubernetes.fabric8.client.istio;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.processExecResult;

/**
 * @author wind57
 */
class Fabric8IstioIT {

	private static final String NAMESPACE = "istio-test";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-istio";

	private static final String ISTIO_PROXY = "istio/proxyv2";

	private static final String ISTIO_PILOT = "istio/pilot";

	private static Util util;

	private static K3sContainer K3S;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S = Commons.container();
		K3S.start();
		util = new Util(K3S);
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Commons.pullImage(ISTIO_PROXY, Commons.ISTIO_VERSION, K3S);
		Commons.loadImage(ISTIO_PROXY, Commons.ISTIO_VERSION, "istioproxy", K3S);
		Commons.pullImage(ISTIO_PILOT, Commons.ISTIO_VERSION, K3S);
		Commons.loadImage(ISTIO_PILOT, Commons.ISTIO_VERSION, "istiopilot", K3S);

		processExecResult(K3S.execInContainer("sh", "-c", "kubectl create namespace istio-test"));
		processExecResult(
				K3S.execInContainer("sh", "-c", "kubectl label namespace istio-test istio-injection=enabled"));

		processExecResult(K3S.execInContainer("sh", "-c",
				"/tmp/istioctl" + " --kubeconfig=/etc/rancher/k3s/k3s.yaml install --set profile=minimal -y"));

		util.setUpIstio(NAMESPACE);

		manifests(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.deleteNamespace("istio-system");
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	@AfterAll
	static void after() {
		manifests(Phase.DELETE);
	}

	@Test
	void test() {
		WebClient client = builder().baseUrl("http://localhost/profiles").build();

		@SuppressWarnings("unchecked")
		List<String> result = client.method(HttpMethod.GET).retrieve().bodyToMono(List.class).retryWhen(retrySpec())
				.block();

		// istio profile is present
		Assertions.assertTrue(result.contains("istio"));
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("istio-deployment.yaml");
		InputStream serviceStream = util.inputStream("istio-service.yaml");
		InputStream ingressStream = util.inputStream("istio-ingress.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);
		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
