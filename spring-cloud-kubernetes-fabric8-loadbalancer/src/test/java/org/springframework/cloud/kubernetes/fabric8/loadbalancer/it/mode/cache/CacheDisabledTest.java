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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
		"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.all-namespaces=false",
		"spring.cloud.kubernetes.client.namespace=a", "spring.cloud.loadbalancer.cache.enabled=false" },
		classes = App.class)
@EnableKubernetesMockClient
class CacheDisabledTest {

	private static final int NUMBER_OF_CALLS = 2;

	private static KubernetesMockServer kubernetesMockServer;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesMockServer.url("/"));
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");

		// these two are needed to populate the Listers and to silence the logs from
		// errors
		// since we are in the SERVICE mode, we don't use the DiscoveryClient, so these
		// two mocks don't play a role in the testing itself.
		Util.mockNamespacedIndexerServiceCall("a", "service-a", kubernetesMockServer);
		Util.mockNamespacedIndexerEndpointsCall("a", "service-a", "localhost", 8080, kubernetesMockServer);

		// mock fabric8 client calls that are made as part of the services list supplier
		Util.mockLoadBalancerServiceCall("a", "service-a", kubernetesMockServer, 8080, "a", NUMBER_OF_CALLS);
	}

	/**
	 * <pre>
	 *      - we disable caching via 'spring.cloud.loadbalancer.cache.enabled=false'
	 *      - as such, two calls to : loadBalancer.choose() will both execute
	 *        on the delegate itself, which we assert via NUMBER_OF_CALLS = 2
	 *        ( if we set it to 1, the test fails )
	 * </pre>
	 */
	@Test
	void test() {

		ReactiveLoadBalancer<ServiceInstance> loadBalancer = loadBalancerClientFactory.getInstance("service-a");
		Response<ServiceInstance> firstResponse = Mono.from(loadBalancer.choose()).block();
		assertThat(firstResponse.hasServer()).isTrue();

		Response<ServiceInstance> secondResponse = Mono.from(loadBalancer.choose()).block();
		assertThat(secondResponse.hasServer()).isTrue();

	}

}
