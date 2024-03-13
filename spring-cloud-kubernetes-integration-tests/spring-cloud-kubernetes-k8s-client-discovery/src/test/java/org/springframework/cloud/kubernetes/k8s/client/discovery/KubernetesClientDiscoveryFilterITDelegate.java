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

package org.springframework.cloud.kubernetes.k8s.client.discovery;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryFilterITDelegate {

	private static final String NAMESPACE_A_UAT = "a-uat";

	private static final String NAMESPACE_B_UAT = "b-uat";

	private static final String NAMESPACE = "default";

	private static final String DEPLOYMENT_NAME = "spring-cloud-kubernetes-k8s-client-discovery";

	void filterMatchesOneNamespaceViaThePredicate(Util util) {

		// set-up for this test and the next one
		util.createNamespace(NAMESPACE_A_UAT);
		util.createNamespace(NAMESPACE_B_UAT);
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_A_UAT, NAMESPACE_B_UAT));
		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.CREATE, false);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.CREATE, false);

		WebClient clientServices = builder().baseUrl("http://localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("service-wiremock"));

		WebClient client = builder().baseUrl("http://localhost/service-instances/service-wiremock").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(serviceInstances.size(), 1);

		DefaultKubernetesServiceInstance first = serviceInstances.get(0);
		Assertions.assertEquals(first.getServiceId(), "service-wiremock");
		Assertions.assertNotNull(first.getInstanceId());
		Assertions.assertEquals(first.getPort(), 8080);
		Assertions.assertEquals(first.getNamespace(), "a-uat");
		Assertions.assertEquals(first.getMetadata(),
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "a-uat", "type", "ClusterIP"));

	}

	/**
	 * <pre>
	 *     - service "wiremock" is present in namespace "a-uat"
	 *     - service "wiremock" is present in namespace "b-uat"
	 *
	 *     - we search with a predicate : "#root.metadata.namespace matches '^uat.*$'"
	 *
	 *     As such, both services are found via 'getInstances' call.
	 * </pre>
	 */
	void filterMatchesBothNamespacesViaThePredicate() {

		// patch the deployment to change what namespaces are take into account
		KubernetesClientDiscoveryClientUtils.patchForTwoNamespacesMatchViaThePredicate(DEPLOYMENT_NAME, NAMESPACE);

		WebClient clientServices = builder().baseUrl("http://localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("service-wiremock"));

		WebClient client = builder().baseUrl("http://localhost/service-instances/service-wiremock").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(serviceInstances.size(), 2);
		List<DefaultKubernetesServiceInstance> sorted = serviceInstances.stream()
				.sorted(Comparator.comparing(DefaultKubernetesServiceInstance::getNamespace)).toList();

		DefaultKubernetesServiceInstance first = sorted.get(0);
		Assertions.assertEquals(first.getServiceId(), "service-wiremock");
		Assertions.assertNotNull(first.getInstanceId());
		Assertions.assertEquals(first.getPort(), 8080);
		Assertions.assertEquals(first.getNamespace(), "a-uat");
		Assertions.assertEquals(first.getMetadata(),
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "a-uat", "type", "ClusterIP"));

		DefaultKubernetesServiceInstance second = sorted.get(1);
		Assertions.assertEquals(second.getServiceId(), "service-wiremock");
		Assertions.assertNotNull(second.getInstanceId());
		Assertions.assertEquals(second.getPort(), 8080);
		Assertions.assertEquals(second.getNamespace(), "b-uat");
		Assertions.assertEquals(second.getMetadata(),
				Map.of("app", "service-wiremock", "port.http", "8080", "k8s_namespace", "b-uat", "type", "ClusterIP"));

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
