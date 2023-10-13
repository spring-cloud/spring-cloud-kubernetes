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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.retrySpec;

final class Fabric8DiscoveryFilterDelegate {

	private Fabric8DiscoveryFilterDelegate() {

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
	static void filterMatchesBothNamespacesViaThePredicate() {

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

	/**
	 * <pre>
	 *     - service "wiremock" is present in namespace "a-uat"
	 *     - service "wiremock" is present in namespace "b-uat"
	 *
	 *     - we search with a predicate : "#root.metadata.namespace matches 'a-uat$'"
	 *
	 *     As such, only service from 'a-uat' namespace matches.
	 * </pre>
	 */
	static void filterMatchesOneNamespaceViaThePredicate() {

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

}
