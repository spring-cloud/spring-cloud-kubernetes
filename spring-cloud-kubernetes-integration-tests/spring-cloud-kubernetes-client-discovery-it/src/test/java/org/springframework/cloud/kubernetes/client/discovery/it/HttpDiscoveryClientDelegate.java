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
import java.util.Arrays;
import java.util.Objects;

import org.assertj.core.api.Assertions;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
final class HttpDiscoveryClientDelegate {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(HttpDiscoveryClientDelegate.class);

	private HttpDiscoveryClientDelegate() {

	}

	static void testHttpDiscoveryClient() {
		testLoadBalancer();
		testHealth();
	}

	private static void testLoadBalancer() {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost/http/services").build();

		String[] result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String[].class)
				.retryWhen(retrySpec()).block();
		assertThat(Arrays.stream(result).anyMatch("spring-cloud-kubernetes-discoveryserver"::equalsIgnoreCase))
				.isTrue();

	}

	private static void testHealth() {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost/actuator/health").build();

		String healthResult = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(
				"$.components.reactiveDiscoveryClients.components.['Reactive Kubernetes Discovery Client'].status")
				.isEqualTo("UP");
	}

	private static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
