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
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
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

	static void testHttpDiscoveryClient(K3sContainer container) {
		testLoadBalancer(container);
		testHealth(container);
	}

	private static void testLoadBalancer(K3sContainer container) {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/http/services").build();

		try {
			String[] result = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String[].class)
					.retryWhen(retrySpec()).block();
			assertThat(Arrays.stream(result).anyMatch("spring-cloud-kubernetes-discoveryserver"::equalsIgnoreCase))
					.isTrue();
		}
		catch (Exception e) {
			try {
				String appPodName = container.execInContainer("sh", "-c",
						"kubectl get pods -l app=spring-cloud-kubernetes-client-discovery-it"
								+ " -o=name --no-headers | tr -d '\n'")
						.getStdout();
				Container.ExecResult execResult = container.execInContainer("sh", "-c",
						"kubectl logs " + appPodName.trim());
				String ok = execResult.getStdout();
				System.out.println(ok);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

	}

	private static void testHealth(K3sContainer container) {
		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl("http://localhost:80/actuator/health").build();

		try {
			String healthResult = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			System.out.println("1111111 " + healthResult);
			Assertions.assertThat(BASIC_JSON_TESTER.from(healthResult)).extractingJsonPathStringValue(
					"$.components.reactiveDiscoveryClients.components.['Reactive Kubernetes Discovery Client'].status")
					.isEqualTo("UP");
		}
		catch (Exception e) {
			try {
				String appPodName = container.execInContainer("sh", "-c",
						"kubectl get pods -l app=spring-cloud-kubernetes-client-discovery-it"
								+ " -o=name --no-headers | tr -d '\n'")
						.getStdout();
				Container.ExecResult execResult = container.execInContainer("sh", "-c",
						"kubectl logs " + appPodName.trim());
				String ok = execResult.getStdout();
				System.out.println(ok);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

	}

	private static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
