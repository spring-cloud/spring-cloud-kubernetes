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

import java.util.List;
import java.util.Map;

import reactor.netty.http.client.HttpClient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Ryan Baxter
 */

@SpringBootApplication
@RestController
public class KubernetesClientLoadBalancerApplication {

	private static final String URL = "http://service-wiremock/__admin/mappings";

	private final DiscoveryClient discoveryClient;

	public KubernetesClientLoadBalancerApplication(DiscoveryClient discoveryClien) {
		this.discoveryClient = discoveryClien;
	}

	public static void main(String[] args) {
		SpringApplication.run(KubernetesClientLoadBalancerApplication.class, args);
	}

	@Bean
	@LoadBalanced
	WebClient.Builder client() {
		return WebClient.builder();
	}

	@GetMapping("/loadbalancer-it/service")
	@SuppressWarnings("unchecked")
	public Map<String, Object> greeting() {
		return (Map<String, Object>) client().clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
				.baseUrl(URL).build().method(HttpMethod.GET).retrieve().bodyToMono(Map.class).block();
	}

	@GetMapping("/services")
	public List<String> services() {
		return discoveryClient.getServices();
	}

}
