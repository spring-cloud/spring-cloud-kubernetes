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

package org.springframework.cloud.kubernetes.fabric8.configmap;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class Fabric8DiscoveryPodMetadataIT {

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

	private static KubernetesClient client;

	private static Util util;

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
		util.busybox(NAMESPACE, Phase.CREATE);
	}

	@AfterAll
	static void after() throws Exception {
		util.busybox(NAMESPACE, Phase.DELETE);
		manifests(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@Test
	void testPodMetadata() throws Exception {

		// find both pods
		String[] both = K3S.execInContainer("sh", "-c", "kubectl get pods -l app=busybox -o=name --no-headers")
				.getStdout().split("\n");
		// add a label to first pod
		K3S.execInContainer("sh", "-c",
				"kubectl label pods " + both[0].split("/")[1] + " custom-label=custom-label-value");
		// add annotation to the second pod
		K3S.execInContainer("sh", "-c",
				"kubectl annotate pods " + both[1].split("/")[1] + " custom-annotation=custom-annotation-value");

		WebClient client = builder().baseUrl("http://localhost/service-instances/busybox-service").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		DefaultKubernetesServiceInstance withCustomLabel = serviceInstances.stream()
				.filter(x -> x.podMetadata().get("annotations").isEmpty()).toList().get(0);
		Assertions.assertEquals(withCustomLabel.getServiceId(), "busybox-service");
		Assertions.assertNotNull(withCustomLabel.getInstanceId());
		Assertions.assertNotNull(withCustomLabel.getHost());
		Assertions.assertEquals(withCustomLabel.getMetadata(),
				Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
		Assertions.assertTrue(withCustomLabel.podMetadata().get("labels").entrySet().stream()
				.anyMatch(x -> x.getKey().equals("custom-label") && x.getValue().equals("custom-label-value")));

		DefaultKubernetesServiceInstance withCustomAnnotation = serviceInstances.stream()
				.filter(x -> !x.podMetadata().get("annotations").isEmpty()).toList().get(0);
		Assertions.assertEquals(withCustomAnnotation.getServiceId(), "busybox-service");
		Assertions.assertNotNull(withCustomAnnotation.getInstanceId());
		Assertions.assertNotNull(withCustomAnnotation.getHost());
		Assertions.assertEquals(withCustomAnnotation.getMetadata(),
				Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
		Assertions.assertTrue(withCustomAnnotation.podMetadata().get("annotations").entrySet().stream().anyMatch(
				x -> x.getKey().equals("custom-annotation") && x.getValue().equals("custom-annotation-value")));
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("fabric8-discovery-deployment.yaml");
		InputStream serviceStream = util.inputStream("fabric8-discovery-service.yaml");
		InputStream ingressStream = util.inputStream("fabric8-discovery-ingress.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).get();

		List<EnvVar> existing = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		existing.add(new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDPODLABELS")
				.withValue("true").build());
		existing.add(new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDPODANNOTATIONS")
				.withValue("true").build());
		existing.add(
				new EnvVarBuilder().withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY")
						.withValue("DEBUG").build());
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(existing);

		Service service = client.services().load(serviceStream).get();
		Ingress ingress = client.network().v1().ingresses().load(ingressStream).get();

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
