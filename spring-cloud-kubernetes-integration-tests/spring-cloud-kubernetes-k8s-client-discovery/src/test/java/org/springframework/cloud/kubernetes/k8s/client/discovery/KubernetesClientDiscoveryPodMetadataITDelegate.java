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
class KubernetesClientDiscoveryPodMetadataITDelegate {

	/**
	 * Three services are deployed in the default namespace. We do not configure any
	 * explicit namespace and 'default' must be picked-up.
	 */
	void testSimple() {

		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(servicesResult.size(), 3);
		Assertions.assertTrue(servicesResult.contains("kubernetes"));
		Assertions.assertTrue(servicesResult.contains("spring-cloud-kubernetes-k8s-client-discovery"));
		Assertions.assertTrue(servicesResult.contains("external-name-service"));

		WebClient ourServiceClient = builder()
				.baseUrl("http://localhost//service-instances/spring-cloud-kubernetes-k8s-client-discovery").build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 1);

		DefaultKubernetesServiceInstance serviceInstance = ourServiceInstances.get(0);
		Assertions.assertNotNull(serviceInstance.getInstanceId());
		Assertions.assertEquals(serviceInstance.getServiceId(), "spring-cloud-kubernetes-k8s-client-discovery");
		Assertions.assertNotNull(serviceInstance.getHost());
		Assertions.assertEquals(serviceInstance.getMetadata(),
				Map.of("port.http", "8080", "k8s_namespace", "default", "type", "ClusterIP", "label-app",
						"spring-cloud-kubernetes-k8s-client-discovery", "annotation-custom-spring-k8s", "spring-k8s"));
		Assertions.assertEquals(serviceInstance.getPort(), 8080);
		Assertions.assertEquals(serviceInstance.getNamespace(), "default");

	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
