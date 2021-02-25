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
import java.util.Collections;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.DefaultServiceInstance;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = KubernetesClientLoadBalancerPodModeTests.App.class)
public class KubernetesClientLoadBalancerPodModeTests {

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
		public KubernetesNamespaceProvider kubernetesNamespaceProvider() {
			KubernetesNamespaceProvider provider = mock(KubernetesNamespaceProvider.class);
			when(provider.getNamespace()).thenReturn("test");
			return provider;
		}

		@Bean
		public KubernetesInformerDiscoveryClient kubernetesInformerDiscoveryClient() {
			KubernetesInformerDiscoveryClient client = mock(KubernetesInformerDiscoveryClient.class);
			ServiceInstance instance = new DefaultServiceInstance("servicea-wiremock1", "servicea-wiremock", "fake",
					8888, false);
			given(client.getInstances(eq("servicea-wiremock"))).willReturn(Collections.singletonList(instance));
			return client;
		}

		@Bean
		@LoadBalanced
		RestTemplate restTemplate() {
			return new RestTemplateBuilder().build();
		}

	}

}
