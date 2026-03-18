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

package org.springframework.cloud.kubernetes.client.loadbalancer.it.mode.service.label;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.loadbalancer.KubernetesClientLabelBasedServicesListSupplier;
import org.springframework.cloud.kubernetes.client.loadbalancer.it.mode.App;
import org.springframework.cloud.kubernetes.client.loadbalancer.it.mode.LoadBalancerConfiguration;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.cloud.kubernetes.client.loadbalancer.it.DiscoveryClientIndexerMocks.mockAllNamespacesIndexerEndpointsCalls;
import static org.springframework.cloud.kubernetes.client.loadbalancer.it.DiscoveryClientIndexerMocks.mockAllNamespacesIndexerServiceCalls;
import static org.springframework.cloud.kubernetes.client.loadbalancer.it.LoadBalancerMocks.mockLoadBalancerServiceCallInAllNamespacesByLabels;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=true",
				"spring.cloud.kubernetes.loadbalancer.service-matching-strategy=LABELS",
				"spring.cloud.kubernetes.discovery.serviceLabels.same-key=same-value" },
		classes = { LoadBalancerConfiguration.class, App.class })
class AllNamespacesTest {

	private static final Map<String, String> NAMESPACE_TO_SERVICE_ID = Map.of("a", "service-a", "b", "service-b");

	private static final String SERVICE_A_URL = "http://service-a/a-path";

	private static final String SERVICE_B_URL = "http://service-b/b-path";

	private static WireMockServer wireMockServer;

	private static MockedStatic<KubernetesClientUtils> clientUtils;

	@SuppressWarnings("rawtypes")
	private static final MockedStatic<KubernetesServiceInstanceMapper> MOCKED_STATIC = Mockito
		.mockStatic(KubernetesServiceInstanceMapper.class);

	@Autowired
	private WebClient.Builder builder;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();

		mockAllNamespacesIndexerServiceCalls(NAMESPACE_TO_SERVICE_ID, wireMockServer);
		mockAllNamespacesIndexerEndpointsCalls(NAMESPACE_TO_SERVICE_ID, wireMockServer);

		mockLoadBalancerServiceCallInAllNamespacesByLabels("a", "service-a", Map.of("same-key", "same-value"),
				wireMockServer);
		mockLoadBalancerServiceCallInAllNamespacesByLabels("b", "service-b", Map.of("same-key", "same-value"),
				wireMockServer);

		// we mock host creation so that it becomes something like : localhost:<port>
		// then wiremock can catch this request, and we can assert for the result
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("service-a", "a", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("service-b", "b", "cluster.local"))
			.thenReturn("localhost");

		ApiClient client = new ClientBuilder().setBasePath("http://localhost:" + wireMockServer.port()).build();
		clientUtils = mockStatic(KubernetesClientUtils.class);
		clientUtils.when(KubernetesClientUtils::kubernetesApiClient).thenReturn(client);

	}

	@AfterAll
	static void afterAll() {
		wireMockServer.stop();
		MOCKED_STATIC.close();
		clientUtils.close();
	}

	/**
	 * <pre>
	 *      - service-a is present in namespace a
	 *      - service-b is present in namespace b
	 *      - we make two calls to them via the load balancer
	 * </pre>
	 */
	@Test
	void test() {

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/a-path"))
			.willReturn(WireMock.aResponse().withBody("service-a-reached").withStatus(200)));

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/b-path"))
			.willReturn(WireMock.aResponse().withBody("service-b-reached").withStatus(200)));

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
		Assertions.assertThat(serviceBResult).isEqualTo("service-b-reached");

		CachingServiceInstanceListSupplier supplier = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getProvider("service-a", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplier.getDelegate().getClass())
			.isSameAs(KubernetesClientLabelBasedServicesListSupplier.class);

	}

}
