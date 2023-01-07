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

package org.springframework.cloud.kubernetes.client.catalog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

public class KubernetesClientCatalogWatchNamespacesIT {

	private static final String APP_NAME = "spring-cloud-kubernetes-client-catalog-watcher";

	private static final String NAMESPACE_A = "namespacea";

	private static final String NAMESPACE_B = "namespaceb";

	private static final String NAMESPACE_DEFAULT = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(APP_NAME, K3S);
		util = new Util(K3S);
		util.setUp(NAMESPACE_DEFAULT);
	}

	@BeforeEach
	void beforeEach() {
		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);
		util.setUpClusterWide(NAMESPACE_DEFAULT, Set.of(NAMESPACE_A, NAMESPACE_B));
		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
	}

	/**
	 * <pre>
	 *     - we deploy one busybox service with 2 replica pods in namespace namespacea
	 *     - we deploy one busybox service with 2 replica pods in namespace namespaceb
	 *     - we enable the search to be made in namespacea and default ones
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete both busybox services in namespacea and namespaceb
	 *     - assert that we receive only spring-cloud-kubernetes-client-catalog-watcher pod
	 * </pre>
	 */
	@Test
	void testCatalogWatchWithEndpoints() throws Exception {
		app(false, Phase.CREATE);
		assertLogStatement("stateGenerator is of type: KubernetesEndpointsCatalogWatch");
		test();
		app(false, Phase.DELETE);
	}

	@Test
	void testCatalogWatchWithEndpointSlices() throws Exception {
		app(true, Phase.CREATE);
		assertLogStatement("stateGenerator is of type: KubernetesEndpointSlicesCatalogWatch");
		test();
		app(true, Phase.DELETE);
	}

	/**
	 * we log in debug mode the type of the StateGenerator we use, be that Endpoints or
	 * EndpointSlices. Here we make sure that in the test we actually use the correct
	 * type.
	 */
	private void assertLogStatement(String log) throws Exception {
		String appPodName = K3S.execInContainer("kubectl", "get", "pods", "-l",
				"app=spring-cloud-kubernetes-client-catalog-watcher", "-o=name", "--no-headers").getStdout();
		String allLogs = K3S.execInContainer("kubectl", "logs", appPodName.trim()).getStdout();
		Assertions.assertTrue(allLogs.contains(log));
	}

	/**
	 * the test is the same for both endpoints and endpoint slices, the set-up for them is
	 * different.
	 */
	private void test() {

		WebClient client = builder().baseUrl("localhost/result").build();
		EndpointNameAndNamespace[] holder = new EndpointNameAndNamespace[4];
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(List.class, EndpointNameAndNamespace.class);

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			if (result != null) {
				// 2 from namespace-a, 2 from namespace-b
				Assertions.assertEquals(result.size(), 4);
				holder[0] = result.get(0);
				holder[1] = result.get(1);
				holder[2] = result.get(2);
				holder[3] = result.get(3);
				return true;
			}

			return false;
		});

		EndpointNameAndNamespace resultOne = holder[0];
		EndpointNameAndNamespace resultTwo = holder[1];
		EndpointNameAndNamespace resultThree = holder[2];
		EndpointNameAndNamespace resultFour = holder[3];

		Assertions.assertTrue(resultOne.endpointName().contains("busybox"));
		Assertions.assertTrue(resultTwo.endpointName().contains("busybox"));
		Assertions.assertTrue(resultThree.endpointName().contains("busybox"));
		Assertions.assertTrue(resultFour.endpointName().contains("busybox"));

		List<EndpointNameAndNamespace> sorted = Arrays.stream(holder)
				.sorted(Comparator.comparing(EndpointNameAndNamespace::namespace)).toList();

		Assertions.assertEquals(NAMESPACE_A, sorted.get(0).namespace());
		Assertions.assertEquals(NAMESPACE_A, sorted.get(1).namespace());
		Assertions.assertEquals(NAMESPACE_B, sorted.get(2).namespace());
		Assertions.assertEquals(NAMESPACE_B, sorted.get(3).namespace());

		util.busybox(NAMESPACE_A, Phase.DELETE);
		util.busybox(NAMESPACE_B, Phase.DELETE);

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();
			// there is no update to receive anymore, as there is nothing in namespacea
			// and namespaceb
			return result.size() == 0;
		});

	}

	private void app(boolean useEndpointSlices, Phase phase) {
		V1Deployment deployment = useEndpointSlices
				? (V1Deployment) util.yaml("app/watcher-endpoint-slices-deployment.yaml")
				: (V1Deployment) util.yaml("app/watcher-endpoints-deployment.yaml");
		V1Service service = (V1Service) util.yaml("app/watcher-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("app/watcher-ingress.yaml");

		if (phase.equals(Phase.CREATE)) {
			V1EnvVar one = new V1EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0")
					.withValue(NAMESPACE_A).build();

			V1EnvVar two = new V1EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1")
					.withValue(NAMESPACE_B).build();

			List<V1EnvVar> existing = new ArrayList<>(
					deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
			existing.add(one);
			existing.add(two);
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(existing);
			util.createAndWait(NAMESPACE_DEFAULT, null, deployment, service, ingress, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE_DEFAULT, deployment, service, ingress);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
