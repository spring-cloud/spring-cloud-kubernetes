package org.spring.framework.kubernetes.loadbalancer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@RestController
public class SimpleLoadBalancerApp {

	public static void main(String[] args) {
		SpringApplication.run(SimpleLoadBalancerApp.class, args);
	}

	@Bean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
	}

	@GetMapping("/greeting")
	public String greeting() {
		return "greeting";
	}

}
