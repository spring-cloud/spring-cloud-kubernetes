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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.App;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.LoadBalancerConfiguration;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=POD", "spring.main.cloud-platform=KUBERNETES",
		"spring.cloud.kubernetes.discovery.all-namespaces=false", "spring.cloud.kubernetes.discovery.namespaces.[0]=a",
		"spring.cloud.kubernetes.discovery.namespaces.[1]=b" },
		classes = { LoadBalancerConfiguration.class, App.class })
@EnableKubernetesMockClient(https = false)
class SelectiveNamespacesTest {

	private static KubernetesMockServer kubernetesMockServer;

	private static final String SERVICE_URL = "http://my-service";

	private static final int SERVICE_A_PORT = TestSocketUtils.findAvailableTcpPort();

	private static final int SERVICE_B_PORT = TestSocketUtils.findAvailableTcpPort();

	private static final int SERVICE_C_PORT = TestSocketUtils.findAvailableTcpPort();

	private static WireMockServer serviceAMockServer;

	private static WireMockServer serviceBMockServer;

	private static WireMockServer serviceCMockServer;

	@Autowired
	private WebClient.Builder builder;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {

		serviceAMockServer = new WireMockServer(SERVICE_A_PORT);
		serviceAMockServer.start();
		WireMock.configureFor("localhost", SERVICE_A_PORT);

		serviceBMockServer = new WireMockServer(SERVICE_B_PORT);
		serviceBMockServer.start();
		WireMock.configureFor("localhost", SERVICE_B_PORT);

		serviceCMockServer = new WireMockServer(SERVICE_C_PORT);
		serviceCMockServer.start();
		WireMock.configureFor("localhost", SERVICE_C_PORT);

		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesMockServer.url("/"));
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");

		Util.mockNamespacedIndexerServiceCall("a", "my-service", kubernetesMockServer);
		Util.mockNamespacedIndexerServiceCall("b", "my-service", kubernetesMockServer);
		Util.mockNamespacedIndexerServiceCall("c", "my-service", kubernetesMockServer);

		// actual pod URL will be : localhost:SERVICE_A_PORT and so on for the rest
		Util.mockNamespacedIndexerEndpointsCall("a", "my-service", "localhost", SERVICE_A_PORT, kubernetesMockServer);
		Util.mockNamespacedIndexerEndpointsCall("b", "my-service", "localhost", SERVICE_B_PORT, kubernetesMockServer);
		Util.mockNamespacedIndexerEndpointsCall("c", "my-service", "localhost", SERVICE_C_PORT, kubernetesMockServer);
	}

	@AfterAll
	static void afterAll() {
		serviceAMockServer.stop();
		serviceBMockServer.stop();
		serviceCMockServer.stop();
	}

	/**
	 * <pre>
	 *      - my-service is present in 'a' namespace
	 *      - my-service is present in 'b' namespace
	 *      - my-service is present in 'c' namespace
	 *      - we enable search in selective namespaces [a, b]
	 *      - load balancer mode is 'POD'
	 *
	 *      - as such, only service in namespace a and b are load balanced
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the POD mode.
	 * </pre>
	 */
	@Test
	void test() {

		serviceAMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
			.willReturn(WireMock.aResponse().withBody("service-a-reached").withStatus(200)));

		serviceBMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
			.willReturn(WireMock.aResponse().withBody("service-b-reached").withStatus(200)));

		serviceCMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
			.willReturn(WireMock.aResponse().withBody("service-c-reached").withStatus(200)));

		String firstCallResult = builder.baseUrl(SERVICE_URL)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();

		String secondCallResult = builder.baseUrl(SERVICE_URL)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();

		String thirdCallResult = builder.baseUrl(SERVICE_URL)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();

		boolean secondCallHappenedOnAPod = true;

		// since selective namespaces is a Set, we need to be careful with assertion order
		if (firstCallResult.equals("service-a-reached")) {
			Assertions.assertThat(secondCallResult).isEqualTo("service-b-reached");
			secondCallHappenedOnAPod = false;
		}
		else {
			Assertions.assertThat(firstCallResult).isEqualTo("service-b-reached");
			Assertions.assertThat(secondCallResult).isEqualTo("service-a-reached");
		}

		// 3-rd call does not happen on "c", because only "a" and "b" are the selective
		// namespaces
		if (secondCallHappenedOnAPod) {
			Assertions.assertThat(thirdCallResult).isEqualTo("service-b-reached");
		}
		else {
			Assertions.assertThat(thirdCallResult).isEqualTo("service-a-reached");
		}

		CachingServiceInstanceListSupplier supplier = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getProvider("my-service", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplier.getDelegate().getClass())
			.isSameAs(DiscoveryClientServiceInstanceListSupplier.class);

	}

}
