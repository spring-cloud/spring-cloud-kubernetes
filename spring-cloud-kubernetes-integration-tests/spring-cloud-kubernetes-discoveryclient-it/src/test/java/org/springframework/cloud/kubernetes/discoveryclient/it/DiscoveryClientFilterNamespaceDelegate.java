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

package org.springframework.cloud.kubernetes.discoveryclient.it;

import java.time.Duration;
import java.util.Objects;

import org.assertj.core.api.Assertions;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author mbialkowski1
 */
final class DiscoveryClientFilterNamespaceDelegate {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(
			DiscoveryClientFilterNamespaceDelegate.class);

	static void testNamespaceDiscoveryClient() {
		testLoadBalancer();
		testHealth();
	}

	private static void testLoadBalancer() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/services").build();
		String result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(result)).extractingJsonPathArrayValue("$")
				.contains("service-wiremock");

		// ServiceInstance
		WebClient serviceInstanceClient = builder
				.baseUrl("http://localhost:80/discoveryclient-it/service/service-wiremock").build();
		String serviceInstances = serviceInstanceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(serviceInstances)).extractingJsonPathStringValue("$.[0].serviceId")
				.isEqualTo("service-wiremock");

		Assertions.assertThat(BASIC_JSON_TESTER.from(serviceInstances)).extractingJsonPathStringValue("$.[0].namespace")
				.isEqualTo("left");
	}

	private static void testHealth() {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/discoveryclient-it/actuator/health").build();

		String health = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		Assertions.assertThat(BASIC_JSON_TESTER.from(health))
				.extractingJsonPathStringValue("$.components.discoveryComposite.status").isEqualTo("UP");
	}

	private static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
