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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
final class Fabric8DiscoveryDelegate {

	/**
	 * KubernetesDiscoveryClient::getServices call must include the external-name-service
	 * also.
	 */
	void testAllServices() {
		WebClient client = builder().baseUrl("http://localhost/services").build();

		List<String> result = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(result.size(), 5);
		Assertions.assertTrue(result.contains("kubernetes"));
		Assertions.assertTrue(result.contains("spring-cloud-kubernetes-fabric8-client-discovery"));
		Assertions.assertTrue(result.contains("service-wiremock"));
		Assertions.assertTrue(result.contains("busybox-service"));
		Assertions.assertTrue(result.contains("external-name-service"));
	}

	void testExternalNameServiceInstance() {

		WebClient client = builder().baseUrl("http://localhost/service-instances/external-name-service").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		DefaultKubernetesServiceInstance result = serviceInstances.get(0);

		Assertions.assertEquals(serviceInstances.size(), 1);
		Assertions.assertEquals(result.getServiceId(), "external-name-service");
		Assertions.assertNotNull(result.getInstanceId());
		Assertions.assertEquals(result.getHost(), "spring.io");
		Assertions.assertEquals(result.getPort(), -1);
		Assertions.assertEquals(result.getMetadata(), Map.of("k8s_namespace", "default", "type", "ExternalName"));
		Assertions.assertFalse(result.isSecure());
		Assertions.assertEquals(result.getUri().toASCIIString(), "spring.io");
		Assertions.assertEquals(result.getScheme(), "http");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
