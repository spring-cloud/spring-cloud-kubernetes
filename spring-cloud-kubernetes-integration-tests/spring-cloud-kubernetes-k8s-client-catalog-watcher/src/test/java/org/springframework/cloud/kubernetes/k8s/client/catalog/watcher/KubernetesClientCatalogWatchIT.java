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

package org.springframework.cloud.kubernetes.k8s.client.catalog.watcher;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.waitForLogStatement;

/**
 * @author wind57
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KubernetesClientCatalogWatchIT {

	private static final String APP_NAME = "spring-cloud-kubernetes-k8s-client-catalog-watcher";

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_A = "namespacea";

	private static final String NAMESPACE_B = "namespaceb";

	private static final K3sContainer K3S = Commons.container();

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + APP_NAME + ":" + Commons.pomVersion();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(APP_NAME, K3S);
		util = new Util(K3S);
		util.setUp(NAMESPACE);
		app(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() {
		util.deleteClusterWide(NAMESPACE, Set.of(NAMESPACE_A, NAMESPACE_B));
		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);
		app(Phase.DELETE);
		Commons.systemPrune();
	}

	@BeforeEach
	void beforeEach() {
		util.busybox(NAMESPACE, Phase.CREATE);
	}

	/**
	 * <pre>
	 *     - we deploy a busybox service with 2 replica pods
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete the busybox service
	 *     - assert that we receive only spring-cloud-kubernetes-client-catalog-watcher pod
	 * </pre>
	 */
	@Test
	@Order(1)
	void testCatalogWatchWithEndpoints() {
		waitForLogStatement("stateGenerator is of type: KubernetesEndpointsCatalogWatch", K3S, APP_NAME);
		test();
	}

	@Test
	@Order(2)
	void testCatalogWatchWithEndpointSlices() {
		KubernetesClientCatalogWatchUtils.patchForEndpointSlices(APP_NAME, NAMESPACE, DOCKER_IMAGE);
		waitForLogStatement("stateGenerator is of type: KubernetesEndpointSlicesCatalogWatch", K3S, APP_NAME);
		test();

		testCatalogWatchWithEndpointsNamespaces();
	}

	void testCatalogWatchWithEndpointsNamespaces() {
		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE_A, NAMESPACE_B));
		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);

		KubernetesClientCatalogWatchUtils.patchForEndpointsNamespaces(APP_NAME, NAMESPACE, DOCKER_IMAGE);
		KubernetesClientCatalogWatchNamespacesDelegate.testCatalogWatchWithEndpointsNamespaces(APP_NAME);

		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);
		KubernetesClientCatalogWatchUtils.patchForEndpointSlicesNamespaces(APP_NAME, NAMESPACE, DOCKER_IMAGE);
		KubernetesClientCatalogWatchNamespacesDelegate.testCatalogWatchWithEndpointSlicesNamespaces(APP_NAME);
	}

	/**
	 * the test is the same for both endpoints and endpoint slices, the set-up for them is
	 * different.
	 */
	private void test() {

		WebClient client = builder().baseUrl("http://localhost/result").build();
		EndpointNameAndNamespace[] holder = new EndpointNameAndNamespace[2];
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(List.class, EndpointNameAndNamespace.class);

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			// we get 3 pods as input, but because they are sorted by name in the catalog
			// watcher implementation
			// we will get the first busybox instances here.

			if (result != null) {
				if (result.size() != 3) {
					return false;
				}
				holder[0] = result.get(0);
				holder[1] = result.get(1);
				return true;
			}

			return false;
		});

		EndpointNameAndNamespace resultOne = holder[0];
		EndpointNameAndNamespace resultTwo = holder[1];

		Assertions.assertNotNull(resultOne);
		Assertions.assertNotNull(resultTwo);

		Assertions.assertTrue(resultOne.endpointName().contains("busybox"));
		Assertions.assertTrue(resultTwo.endpointName().contains("busybox"));
		Assertions.assertEquals("default", resultOne.namespace());
		Assertions.assertEquals("default", resultTwo.namespace());

		util.busybox(NAMESPACE, Phase.DELETE);

		// what we get after delete
		EndpointNameAndNamespace[] afterDelete = new EndpointNameAndNamespace[1];

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			// we need to get the event from KubernetesCatalogWatch, but that happens
			// on periodic bases. So in order to be sure we got the event we care about
			// we wait until the result has a single entry, which means busybox was
			// deleted
			// + KubernetesCatalogWatch received the new update.
			if (result != null && result.size() != 1) {
				return false;
			}

			// we will only receive one pod here, our own
			if (result != null) {
				afterDelete[0] = result.get(0);
				return true;
			}

			return false;
		});

		Assertions.assertTrue(afterDelete[0].endpointName().contains(APP_NAME));
		Assertions.assertEquals("default", afterDelete[0].namespace());

	}

	private static void app(Phase phase) {
		V1Deployment deployment = (V1Deployment) util.yaml("app/watcher-deployment.yaml");
		V1Service service = (V1Service) util.yaml("app/watcher-service.yaml");
		V1Ingress ingress = (V1Ingress) util.yaml("app/watcher-ingress.yaml");

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
