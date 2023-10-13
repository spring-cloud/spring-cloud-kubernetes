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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
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

final class KubernetesClientCatalogWatchNamespacesDelegate {

	private KubernetesClientCatalogWatchNamespacesDelegate() {

	}

	private static final String NAMESPACE_A = "namespacea";

	private static final String NAMESPACE_B = "namespaceb";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

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
	static void testCatalogWatchWithEndpointsNamespaces(String deploymentName) {
		waitForLogStatement("stateGenerator is of type: KubernetesEndpointsCatalogWatch", K3S, deploymentName);
		testForNamespacesFilter();
	}

	static void testCatalogWatchWithEndpointSlicesNamespaces(String deploymentName) {
		waitForLogStatement("stateGenerator is of type: KubernetesEndpointSlicesCatalogWatch", K3S, deploymentName);
		testForNamespacesFilter();
	}

	/**
	 * the test is the same for both endpoints and endpoint slices, the set-up for them is
	 * different.
	 */
	private static void testForNamespacesFilter() {

		WebClient client = builder().baseUrl("http://localhost/result").build();
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

		util = new Util(K3S);
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

	private static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
