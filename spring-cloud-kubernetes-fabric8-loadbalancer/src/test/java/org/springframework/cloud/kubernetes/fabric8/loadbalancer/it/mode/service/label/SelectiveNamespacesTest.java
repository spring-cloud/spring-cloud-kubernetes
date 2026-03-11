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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.service.label;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8LabelBasedServicesListSupplier;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.App;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.LoadBalancerConfiguration;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.DiscoveryClientIndexerMocks.mockNamespacedIndexerEndpointsCallByLabels;
import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.DiscoveryClientIndexerMocks.mockNamespacedIndexerServiceCallByLabels;
import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.LoadbalancerMocks.mockLoadBalancerServiceCallByLabels;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
		"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.all-namespaces=false",
		"spring.cloud.kubernetes.discovery.namespaces.[0]=a", "spring.cloud.kubernetes.discovery.namespaces.[1]=b",
		"spring.cloud.kubernetes.loadbalancer.service-matching-strategy=LABELS",
		"spring.cloud.kubernetes.discovery.serviceLabels.same-key=same-value" },
		classes = { LoadBalancerConfiguration.class, App.class })
@ExtendWith(OutputCaptureExtension.class)
@EnableKubernetesMockClient(https = false)
class SelectiveNamespacesTest {

	private static KubernetesMockServer kubernetesMockServer;

	private static final String MY_SERVICE = "http://my-service";

	private static final int SERVICE_A_PORT = TestSocketUtils.findAvailableTcpPort();

	private static final int SERVICE_B_PORT = TestSocketUtils.findAvailableTcpPort();

	private static final Map<String, String> SERVICE_LABELS = Map.of("same-key", "same-value");

	private static WireMockServer serviceAMockServer;

	private static WireMockServer serviceBMockServer;

	@SuppressWarnings("rawtypes")
	private static final MockedStatic<KubernetesServiceInstanceMapper> MOCKED_STATIC = Mockito
		.mockStatic(KubernetesServiceInstanceMapper.class);

	@Autowired
	private WebClient.Builder builder;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {

		// we mock host creation so that it becomes something like : localhost:<port>
		// then wiremock can catch this request, and we can assert for the result
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "a", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "b", "cluster.local"))
			.thenReturn("localhost");

		serviceAMockServer = new WireMockServer(SERVICE_A_PORT);
		serviceAMockServer.start();

		serviceBMockServer = new WireMockServer(SERVICE_B_PORT);
		serviceBMockServer.start();

		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesMockServer.url("/"));
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");

		mockNamespacedIndexerServiceCallByLabels("a", kubernetesMockServer);
		mockNamespacedIndexerServiceCallByLabels("b", kubernetesMockServer);

		mockNamespacedIndexerEndpointsCallByLabels("a", kubernetesMockServer);
		mockNamespacedIndexerEndpointsCallByLabels("b", kubernetesMockServer);

		mockLoadBalancerServiceCallByLabels("a", "my-service", SERVICE_LABELS, kubernetesMockServer, SERVICE_A_PORT, 1);
		mockLoadBalancerServiceCallByLabels("b", "my-service", SERVICE_LABELS, kubernetesMockServer, SERVICE_B_PORT, 1);

	}

	@AfterAll
	static void afterAll() {
		serviceAMockServer.stop();
		serviceBMockServer.stop();
		MOCKED_STATIC.close();
	}

	/**
	 * <pre>
	 *      - my-service is present in 'a' namespace
	 *      - my-service is present in 'b' namespace
	 *      - we enable search in selective namespaces [a, b]
	 *      - load balancer mode is 'SERVICE'
	 *      - both services share the same labels, so both are discovered
	 *
	 *      - as such, only service in namespace a and b are load balanced
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		serviceAMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
			.willReturn(WireMock.aResponse().withBody("service-a-reached").withStatus(200)));

		serviceBMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
			.willReturn(WireMock.aResponse().withBody("service-b-reached").withStatus(200)));

		serviceAMockServer.addMockServiceRequestListener((request, response) -> {
			System.out.println("SERVER A RECEIVED: " + request.getMethod() + " " + request.getUrl());
		});

		serviceBMockServer.addMockServiceRequestListener((request, response) -> {
			System.out.println("SERVER B RECEIVED: " + request.getMethod() + " " + request.getUrl());
		});

		String firstCallResult = builder.baseUrl(MY_SERVICE)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();

		String secondCallResult = builder.baseUrl(MY_SERVICE)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();

		// since selective namespaces is a Set, we need to be careful with assertion order
		if (firstCallResult.equals("service-a-reached")) {
			Assertions.assertThat(secondCallResult).isEqualTo("service-b-reached");
		}
		else {
			Assertions.assertThat(firstCallResult).isEqualTo("service-b-reached");
			Assertions.assertThat(secondCallResult).isEqualTo("service-a-reached");
		}

		CachingServiceInstanceListSupplier supplier = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getProvider("my-service", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplier.getDelegate().getClass()).isSameAs(Fabric8LabelBasedServicesListSupplier.class);

		Assertions.assertThat(output.getOut()).contains("discovering services in selective namespaces : [a, b]");
	}

}
