/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.cache;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.App;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=false", "spring.cloud.kubernetes.discovery.namespaces_0=a",
				"spring.cloud.loadbalancer.cache.enabled=true", "spring.cloud.loadbalancer.cache.ttl=2s" },
		classes = App.class)
@DirtiesContext
@EnableKubernetesMockClient
class CacheEnabledWithinTTLTest {

	private static final int NUMBER_OF_CALLS = 1;

	private static KubernetesMockServer kubernetesMockServer;

	private static KubernetesClient kubernetesClient;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		Util.mockIndexerServiceCalls("a", "service-a", kubernetesMockServer);
		Util.mockIndexerEndpointsCall("a", "service-a", kubernetesMockServer);
		Util.mockLoadBalancerServiceCall("a", "service-a", kubernetesMockServer, 8080, "a", NUMBER_OF_CALLS);
	}

	/**
	 * <pre>
	 *      - caching is enabled and : 'spring.cloud.loadbalancer.cache.ttl=2s'
	 *      - we make two calls within those two seconds
	 *      - as such, first loadBalancer.choose() will execute on the delegate,
	 *        while the second one will be cached.
	 *
	 * </pre>
	 */
	@Test
	void testCallsWithinTTL() {

		ReactiveLoadBalancer<ServiceInstance> loadBalancer = loadBalancerClientFactory.getInstance("service-a");
		Response<ServiceInstance> firstResponse = Mono.from(loadBalancer.choose()).block();
		assertThat(firstResponse.hasServer()).isTrue();
		Response<ServiceInstance> secondResponse = Mono.from(loadBalancer.choose()).block();
		assertThat(secondResponse.hasServer()).isTrue();

	}

}
