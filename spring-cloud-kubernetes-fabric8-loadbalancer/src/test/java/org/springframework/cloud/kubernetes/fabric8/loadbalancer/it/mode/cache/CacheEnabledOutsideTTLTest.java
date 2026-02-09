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
import org.junit.jupiter.api.AfterAll;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=false", "spring.cloud.kubernetes.client.namespace=a",
				"spring.cloud.loadbalancer.cache.enabled=true", "spring.cloud.loadbalancer.cache.ttl=2s" },
		classes = App.class)
@EnableKubernetesMockClient
class CacheEnabledOutsideTTLTest {

	private static final int NUMBER_OF_CALLS = 2;

	private static KubernetesMockServer kubernetesMockServer;

	private static KubernetesClient kubernetesClient;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");

		Util.mockIndexerServiceCalls("a", "service-a", kubernetesMockServer);
		Util.mockIndexerEndpointsCall("a", "service-a", kubernetesMockServer);
		Util.mockLoadBalancerServiceCall("a", "service-a", kubernetesMockServer, 8080, "a", NUMBER_OF_CALLS);
	}

	@AfterAll
	static void afterAll() {

	}

	/**
	 * <pre>
	 *      - caching is enabled and : 'spring.cloud.loadbalancer.cache.ttl=2s'
	 *      - we make one call now, and one after 2s.
	 *      - as such, first loadBalancer.choose() will execute on the delegate,
	 *        it will be cached. And the second call, since it got TTL-ed, will happen
	 *        on the delegate again.
	 *      - the assertion is done via NUMBER_OF_CALLS. It the value is set to 1,
	 *        the test would fail.
	 * </pre>
	 */
	@Test
	void testCallsOutsideTTL() throws InterruptedException {

		ReactiveLoadBalancer<ServiceInstance> loadBalancer = loadBalancerClientFactory.getInstance("service-a");
		Mono.from(loadBalancer.choose()).block();
		// ttl will expire the first flux
		Thread.sleep(2500);
		Response<ServiceInstance> response = Mono.from(loadBalancer.choose()).block();
		assertThat(response.hasServer()).as("there should be one server").isTrue();
	}

}
