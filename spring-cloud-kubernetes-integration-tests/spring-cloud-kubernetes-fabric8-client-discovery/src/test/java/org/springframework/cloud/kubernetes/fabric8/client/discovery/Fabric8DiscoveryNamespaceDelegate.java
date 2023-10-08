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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.util.List;

import io.fabric8.kubernetes.api.model.Endpoints;
import org.junit.jupiter.api.Assertions;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.retrySpec;

/**
 * @author mbialkowski1
 */
final class Fabric8DiscoveryNamespaceDelegate {

	private Fabric8DiscoveryNamespaceDelegate() {

	}

	static void namespaceFilter() {
		WebClient clientServices = builder().baseUrl("http://localhost/services").build();

		@SuppressWarnings("unchecked")
		List<String> services = (List<String>) clientServices.method(HttpMethod.GET).retrieve().bodyToMono(List.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertEquals(services.size(), 1);
		Assertions.assertTrue(services.contains("service-wiremock"));

		WebClient clientEndpoints = builder().baseUrl("http://localhost/endpoints/service-wiremock").build();

		List<Endpoints> endpoints = clientEndpoints.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<Endpoints>>() {
				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(endpoints.size(), 1);
		Assertions.assertEquals(endpoints.get(0).getMetadata().getNamespace(), "namespace-left");

	}

}
