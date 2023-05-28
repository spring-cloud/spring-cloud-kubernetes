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

package org.springframework.cloud.kubernetes.client.discovery.it;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryPodMetadataIT {

	private static final String NAMESPACE = "default";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-client-discovery-it";

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		//Commons.systemPrune();
	}

	@AfterAll
	static void after() throws Exception {
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	@BeforeEach
	void beforeEach() {
		util.setUp(NAMESPACE);
		manifests(Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		manifests(Phase.DELETE);
	}

	/**
	 * Three services are deployed in the default namespace. We do not configure any
	 * explicit namespace and 'default' must be picked-up.
	 */
	@Test
	void testSimple() {

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(servicesResult.size(), 2);
		Assertions.assertTrue(servicesResult.contains("kubernetes"));
		Assertions.assertTrue(servicesResult.contains("spring-cloud-kubernetes-client-discovery-it"));

		WebClient ourServiceClient = builder()
				.baseUrl("http://localhost//service-instances/spring-cloud-kubernetes-client-discovery-it").build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = ourServiceInstances.get(0);
		Assertions.assertNotNull(serviceInstance.getInstanceId());
		Assertions.assertEquals(serviceInstance.getServiceId(), "spring-cloud-kubernetes-client-discovery-it");
		Assertions.assertNotNull(serviceInstance.getHost());
		Assertions.assertEquals(serviceInstance.getMetadata(),
				Map.of("http", "8080", "k8s_namespace", "default", "type", "ClusterIP", "label-app",
						"spring-cloud-kubernetes-client-discovery-it", "annotation-custom-spring-k8s", "spring-k8s"));
		Assertions.assertEquals(serviceInstance.getPort(), 8080);
		Assertions.assertEquals(serviceInstance.getNamespace(), "default");

	}

	private static void manifests(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("kubernetes-discovery-deployment.yaml");
		V1Service service = (V1Service) util.yaml("kubernetes-discovery-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("kubernetes-discovery-ingress.yaml");

		List<V1EnvVar> envVars = new ArrayList<>(
				Optional.ofNullable(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
						.orElse(List.of()));
		V1EnvVar debugLevel = new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY")
				.value("DEBUG");
		V1EnvVar includeLabelMetadata = new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDLABELS")
				.value("TRUE");
		V1EnvVar includeLabelMetadataPrefix = new V1EnvVar()
				.name("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_LABELSPREFIX").value("label-");
		V1EnvVar includeAnnotationsMetadata = new V1EnvVar()
				.name("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDANNOTATIONS").value("TRUE");
		V1EnvVar includeAnnotationsMetadataPrefix = new V1EnvVar()
				.name("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ANNOTATIONSPREFIX").value("annotation-");

		envVars.add(debugLevel);
		envVars.add(includeLabelMetadata);
		envVars.add(includeLabelMetadataPrefix);
		envVars.add(includeAnnotationsMetadata);
		envVars.add(includeAnnotationsMetadataPrefix);
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else if (phase.equals(Phase.DELETE)) {
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
