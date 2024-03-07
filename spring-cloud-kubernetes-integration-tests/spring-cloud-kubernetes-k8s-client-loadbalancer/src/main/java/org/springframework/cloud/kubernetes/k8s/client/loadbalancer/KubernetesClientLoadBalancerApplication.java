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

package org.springframework.cloud.kubernetes.k8s.client.loadbalancer;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 */

@SpringBootApplication
@RestController
class KubernetesClientLoadBalancerApplication {

	private static final String URL = "http://service-wiremock/__admin/mappings";

	private final WebClient.Builder client;

	private final ObjectProvider<LoadBalancerClientFactory> loadBalancerClientFactory;

	KubernetesClientLoadBalancerApplication(WebClient.Builder client,
			ObjectProvider<LoadBalancerClientFactory> loadBalancerClientFactory) {
		this.client = client;
		this.loadBalancerClientFactory = loadBalancerClientFactory;
	}

	public static void main(String[] args) {
		SpringApplication.run(KubernetesClientLoadBalancerApplication.class, args);
	}

	@GetMapping("/loadbalancer-it/service")
	@SuppressWarnings("unchecked")
	Map<String, Object> greeting() {
		return (Map<String, Object>) client.baseUrl(URL).build().method(HttpMethod.GET).retrieve().bodyToMono(Map.class)
				.block();
	}

	@GetMapping("/loadbalancer-it/supplier")
	String supplier() {
		ServiceInstanceListSupplier supplier = loadBalancerClientFactory.getIfAvailable()
				.getInstance("service-wiremock", ServiceInstanceListSupplier.class);
		if (supplier instanceof CachingServiceInstanceListSupplier cachingSupplier) {
			return cachingSupplier.getDelegate().getClass().getSimpleName();
		}
		return supplier.getClass().getSimpleName();
	}

}
