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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.pod;

import java.util.Map;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.App;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.LoadBalancerConfiguration;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.DiscoveryClientIndexerMocks.mockAllNamespacesIndexerEndpointsCalls;
import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.DiscoveryClientIndexerMocks.mockAllNamespacesIndexerServiceCalls;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=POD", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=true" },
		classes = { LoadBalancerConfiguration.class, App.class })
@EnableKubernetesMockClient(https = false)
class AllNamespacesTest {

	private static final String SERVICE_A_URL = "http://service-a/a-path";

	private static final String SERVICE_B_URL = "http://service-b/b-path";

	private static final Map<String, String> NAMESPACE_TO_SERVICE_ID = Map.of("a", "service-a", "b", "service-b");

	private static KubernetesMockServer kubernetesMockServer;

	@Autowired
	private WebClient.Builder builder;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {

		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesMockServer.url("/"));
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");

		mockAllNamespacesIndexerServiceCalls(NAMESPACE_TO_SERVICE_ID, kubernetesMockServer);
		mockAllNamespacesIndexerEndpointsCalls(NAMESPACE_TO_SERVICE_ID, kubernetesMockServer);

		// this url is generated from Endpoints host and port
		kubernetesMockServer.expect().get().withPath("/a-path").andReturn(200, "service-a-reached").once();
		kubernetesMockServer.expect().get().withPath("/b-path").andReturn(200, "service-b-reached").once();
	}

	/**
	 * <pre>
	 *      - service-a is present in namespace a
	 *      - service-b is present in namespace b
	 *      - we make two calls to them via the load balancer
	 *
	 *      - we first mock services call for the indexer via : mockIndexerServiceCallsInAllNamespaces
	 *      - then we mock endpoints call for the indexer via : mockIndexerEndpointsCallInAllNamespaces
	 *        The difference is that the second one also takes a 'host' and 'port' as argument.
	 *        This is needed because when load balancer makes the call to : /service-a/a-path, it needs
	 *        to know where to re-direct this call to. In order to do that, it looks at data stored in Endpoints
	 *        and computes that path. By providing those two fields, we can mock this path and then assert for it.
	 * </pre>
	 */
	@Test
	void test() {

		String serviceAResult = builder.baseUrl(SERVICE_A_URL)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();
		Assertions.assertThat(serviceAResult).isEqualTo("service-a-reached");

		String serviceBResult = builder.baseUrl(SERVICE_B_URL)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();
		assertThat(serviceBResult).isEqualTo("service-b-reached");

		CachingServiceInstanceListSupplier supplier = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getProvider("service-a", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		assertThat(supplier.getDelegate().getClass()).isSameAs(DiscoveryClientServiceInstanceListSupplier.class);

	}

}
