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

package org.springframework.cloud.kubernetes.client.loadbalancer.it;

import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * @author Ryan Baxter
 */

@SpringBootApplication
@RestController
public class KubernetesClientLoadBalancerApplicationIt {

	private final DiscoveryClient discoveryClient;

	public KubernetesClientLoadBalancerApplicationIt(DiscoveryClient discoveryClien) {
		this.discoveryClient = discoveryClien;
	}

	public static void main(String[] args) {
		SpringApplication.run(KubernetesClientLoadBalancerApplicationIt.class, args);
	}

	@Bean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
	}

	@GetMapping("/servicea")
	public Map<String, Object> greeting() {
		return restTemplate().getForObject("http://servicea-wiremock/__admin/mappings", Map.class);
	}

	@GetMapping("/services")
	public List<String> services() {
		return discoveryClient.getServices();
	}

}
