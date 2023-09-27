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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchIT.NAMESPACE_A;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchIT.NAMESPACE_B;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.retrySpec;

/**
 * @author wind57
 */
final class Fabric8CatalogWatchWithNamespacesDelegate {

	private Fabric8CatalogWatchWithNamespacesDelegate() {

	}

	private static final String APP_NAME = "spring-cloud-kubernetes-fabric8-client-catalog-watcher";

	/**
	 * <pre>
	 *     - we deploy one busybox service with 2 replica pods in namespace namespacea
	 *     - we deploy one busybox service with 2 replica pods in namespace namespaceb
	 *     - we enable the search to be made in namespacea and default ones
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete both busybox services in namespacea and namespaceb
	 *     - assert that we receive only spring-cloud-kubernetes-fabric8-client-catalog-watcher pod
	 * </pre>
	 */
	static void testCatalogWatchWithNamespaceFilterAndEndpoints(K3sContainer container, String imageName, Util util) {
		Commons.waitForLogStatement("stateGenerator is of type: Fabric8EndpointsCatalogWatch", container, imageName);
		test(util);
	}

	static void testCatalogWatchWithNamespaceFilterAndEndpointSlices(K3sContainer container, String imageName,
			Util util) {
		Commons.waitForLogStatement("stateGenerator is of type: Fabric8EndpointSliceV1CatalogWatch", container,
				imageName);
		test(util);
	}

	/**
	 * the test is the same for both endpoints and endpoint slices, the set-up for them is
	 * different.
	 */
	@SuppressWarnings("unchecked")
	private static void test(Util util) {

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
		Assertions.assertEquals(NAMESPACE_A, resultOne.namespace());
		Assertions.assertEquals(NAMESPACE_A, resultTwo.namespace());

		util.busybox(NAMESPACE_A, Phase.DELETE);
		util.busybox(NAMESPACE_B, Phase.DELETE);

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

}
