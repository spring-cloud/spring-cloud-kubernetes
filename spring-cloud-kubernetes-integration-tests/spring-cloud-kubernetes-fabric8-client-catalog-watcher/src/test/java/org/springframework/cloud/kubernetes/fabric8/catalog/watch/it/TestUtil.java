/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch.it;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class TestUtil {

	private TestUtil() {

	}

	static void assertLogStatement(CapturedOutput output, String textToAssert) {
		Awaitility.await().during(Duration.ofSeconds(5))
			.pollInterval(Duration.ofMillis(200))
			.untilAsserted(() -> Assertions.assertThat(output.getOut()).contains(textToAssert));
	}

	/**
	 * the checks are the same for both endpoints and endpoint slices,
	 * while the set-up for them is different.
	 */
	@SuppressWarnings("unchecked")
	static void test(Util util, String namespace, int port) {

		WebClient client = builder().baseUrl("http://localhost:" + port + "/result").build();
		EndpointNameAndNamespace[] holder = new EndpointNameAndNamespace[2];
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(List.class, EndpointNameAndNamespace.class);

		await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(30)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
				.retryWhen(retrySpec())
				.block();

			if (result != null) {
				if (result.size() != 2) {
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

		Assertions.assertThat(resultOne).isNotNull();
		Assertions.assertThat(resultTwo).isNotNull();

		Assertions.assertThat(resultOne.endpointName()).contains("busybox");
		Assertions.assertThat(resultTwo.endpointName()).contains("busybox");

		Assertions.assertThat(resultOne.namespace()).isEqualTo("default");
		Assertions.assertThat(resultTwo.namespace()).isEqualTo("default");

		util.busybox(namespace, Phase.DELETE);

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
				.retrieve()
				.bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
				.retryWhen(retrySpec())
				.block();

			// we need to get the event from KubernetesCatalogWatch, but that happens
			// on periodic bases. So in order to be sure we got the event we care about
			// we wait until there is no entry, which means busybox was deleted
			// and KubernetesCatalogWatch received that update.
			return result.isEmpty();
		});

	}

	private static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
