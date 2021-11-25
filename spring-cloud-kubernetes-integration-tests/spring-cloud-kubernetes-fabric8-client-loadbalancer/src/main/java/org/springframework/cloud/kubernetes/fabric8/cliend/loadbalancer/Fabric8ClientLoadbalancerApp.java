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

package org.springframework.cloud.kubernetes.fabric8.cliend.loadbalancer;

import java.net.URI;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@RestController
public class Fabric8ClientLoadbalancerApp {

	private final DiscoveryClient discoveryClient;

	public Fabric8ClientLoadbalancerApp(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
	}

	public static void main(String[] args) {
		SpringApplication.run(Fabric8ClientLoadbalancerApp.class, args);
	}

	@Bean
	@LoadBalanced
	WebClient.Builder builder() {
		return WebClient.builder();
	}

	@GetMapping("/servicea")
	public Mono<Map> greeting() {
		return builder().build().method(HttpMethod.GET).uri(URI.create("http://servicea-wiremock/__admin/mappings"))
				.retrieve().bodyToMono(Map.class);
	}

	@GetMapping("/services")
	public List<String> services() {
		return discoveryClient.getServices();
	}

}
