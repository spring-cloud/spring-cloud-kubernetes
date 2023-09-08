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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.discovery.KubernetesServiceInstance;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
final class HttpDiscoveryClientFilterNamespaceDelegate {

	private HttpDiscoveryClientFilterNamespaceDelegate() {

	}

	static void testDiscoveryClient() {
		testLoadBalancer();
		testHealth();
	}

	private static void testLoadBalancer() {

		//TODO wind57
		WebClient.Builder one = builder();
		WebClient health = one.baseUrl("http://localhost:80//actuator/health").build();
		String healthResult = health.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
			.retryWhen(retrySpec()).block();
		System.out.println(healthResult);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/http/services").build();

		String[] result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String[].class)
				.retryWhen(retrySpec()).block();
		assertThat(result).containsAnyOf("service-wiremock");

		// ServiceInstance
		WebClient serviceInstanceClient = builder.baseUrl("http://localhost:80/service/service-wiremock").build();
		List<KubernetesServiceInstance> serviceInstances = serviceInstanceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<KubernetesServiceInstance>>() {
				}).retryWhen(retrySpec()).block();

		assertThat(serviceInstances).isNotNull();
		assertThat(serviceInstances.size()).isEqualTo(1);
		assertThat(serviceInstances.get(0).getNamespace()).isEqualTo("a");

	}

	@SuppressWarnings("unchecked")
	private static void testHealth() {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/actuator/health").build();

		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> health = (Map<String, Object>) serviceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType())).retryWhen(retrySpec())
				.block();

		Map<String, Object> components = (Map<String, Object>) health.get("components");

		Map<String, Object> discoveryComposite = (Map<String, Object>) components.get("discoveryComposite");
		assertThat(discoveryComposite.get("status")).isEqualTo("UP");
	}

	private static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
