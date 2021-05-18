/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.loadbalancer;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = KubernetesClientLoadBalancerServiceModeTests.App.class,
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE" })
public class KubernetesClientLoadBalancerServiceModeTests {

	private static final V1ServiceList SERVICE_LIST = new V1ServiceListBuilder()
			.addToItems(
					new V1ServiceBuilder()
							.withMetadata(new V1ObjectMetaBuilder().withName("servicea-wiremock")
									.withNamespace("default").withResourceVersion("1").addToLabels("beta", "true")
									.addToAnnotations("org.springframework.cloud", "true").withUid("0").build())
							.withSpec(new V1ServiceSpecBuilder().withClusterIP("10.96.0.1").withSessionAffinity("None")
									.withType("ClusterIP")
									.addToPorts(new V1ServicePortBuilder().withPort(80).withName("http")
											.withProtocol("TCP").withNewTargetPort(8080).build())
									.build())
							.build())
			.build();

	@Autowired
	private RestTemplate restTemplate;

	@Test
	public void testLoadBalancer() {
		String resp = restTemplate.getForObject("http://servicea-wiremock", String.class);
		assertThat(resp).isEqualTo("hello");
	}

	@RestController
	@SpringBootApplication
	static class App {

		public static void main(String[] args) {
			SpringApplication.run(App.class, args);
		}

		@Bean
		public ApiClient apiClient() {
			return new ClientBuilder().build();
		}

		@Bean
		public CoreV1Api coreV1Api() {
			CoreV1Api coreV1Api = mock(CoreV1Api.class);
			try {
				when(coreV1Api.listNamespacedService(eq("default"), eq(null), eq(null), eq(null),
						eq("metadata.name=servicea-wiremock"), eq(null), eq(null), eq(null), eq(null), eq(null),
						eq(null))).thenReturn(SERVICE_LIST);
			}
			catch (ApiException e) {
				e.printStackTrace();
			}
			return coreV1Api;
		}

		@Bean
		public BlockingLoadBalancerClient blockingLoadBalancerClient() {
			BlockingLoadBalancerClient client = mock(BlockingLoadBalancerClient.class);
			try {
				ClientHttpResponse response = new MockClientHttpResponse("hello".getBytes(), HttpStatus.OK);
				when(client.execute(eq("servicea-wiremock"), any(LoadBalancerRequest.class))).thenReturn(response);
				when(client.execute(eq("servicea-wiremock"), any(ServiceInstance.class),
						any(LoadBalancerRequest.class))).thenReturn(response);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			return client;
		}

		@Bean
		public KubernetesInformerDiscoveryClient kubernetesInformerDiscoveryClient() {
			// Mock this so the real implementation does not try to connect to the K8S API
			// Server
			KubernetesInformerDiscoveryClient client = mock(KubernetesInformerDiscoveryClient.class);
			return client;
		}

		@Bean
		public KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider provider = mock(KubernetesNamespaceProvider.class);
			when(provider.getNamespace()).thenReturn("test");
			return provider;
		}

		@Bean
		@LoadBalanced
		RestTemplate restTemplate() {
			return new RestTemplateBuilder().build();
		}

	}

}
