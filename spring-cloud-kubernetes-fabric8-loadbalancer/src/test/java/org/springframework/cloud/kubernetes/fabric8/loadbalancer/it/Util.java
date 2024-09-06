/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
public final class Util {

	private Util() {

	}

	public static Service service(String namespace, String name, int port) {
		return new ServiceBuilder().withNewMetadata()
			.withNamespace(namespace)
			.withName(name)
			.endMetadata()
			.withSpec(
					new ServiceSpecBuilder().withPorts(new ServicePortBuilder().withName("http").withPort(port).build())
						.build())
			.build();
	}

	public static Endpoints endpoints(int port, String host, String namespace) {
		return new EndpointsBuilder()
			.withSubsets(new EndpointSubsetBuilder().withPorts(new EndpointPortBuilder().withPort(port).build())
				.withAddresses(new EndpointAddressBuilder().withIp(host).build())
				.build())
			.withMetadata(new ObjectMetaBuilder().withName("random-name").withNamespace(namespace).build())
			.build();
	}

	@TestConfiguration
	public static class LoadBalancerConfiguration {

		@Bean
		@LoadBalanced
		WebClient.Builder client() {
			return WebClient.builder();
		}

	}

	@SpringBootApplication
	public static class Configuration {

		public static void main(String[] args) {
			SpringApplication.run(Configuration.class);
		}

	}

}
