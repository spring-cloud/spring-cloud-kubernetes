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

package org.springframework.cloud.kubernetes.client.loadbalancer.it.mode.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1EndpointsListBuilder;
import io.kubernetes.client.openapi.models.V1ListMetaBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.util.ClientBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.client.loadbalancer.KubernetesClientServicesListSupplier;
import org.springframework.cloud.kubernetes.client.loadbalancer.it.Util;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.cloud.kubernetes.client.loadbalancer.it.Util.Configuration;
import static org.springframework.cloud.kubernetes.client.loadbalancer.it.Util.LoadBalancerConfiguration;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=true" },
		classes = { LoadBalancerConfiguration.class, Configuration.class })
class AllNamespacesTest {

	private static final String SERVICE_A_URL = "http://service-a";

	private static final String SERVICE_B_URL = "http://service-b";

	private static final int SERVICE_A_PORT = 8888;

	private static final int SERVICE_B_PORT = 8889;

	private static WireMockServer wireMockServer;

	private static WireMockServer serviceAMockServer;

	private static WireMockServer serviceBMockServer;

	private static MockedStatic<KubernetesClientUtils> clientUtils;

	private static final MockedStatic<KubernetesServiceInstanceMapper> MOCKED_STATIC = Mockito
		.mockStatic(KubernetesServiceInstanceMapper.class);

	@Autowired
	private WebClient.Builder builder;

	@Autowired
	private ObjectProvider<LoadBalancerClientFactory> loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());
		mockWatchers();

		serviceAMockServer = new WireMockServer(SERVICE_A_PORT);
		serviceAMockServer.start();
		WireMock.configureFor("localhost", SERVICE_A_PORT);

		serviceBMockServer = new WireMockServer(SERVICE_B_PORT);
		serviceBMockServer.start();
		WireMock.configureFor("localhost", SERVICE_B_PORT);

		// we mock host creation so that it becomes something like : localhost:8888
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
		serviceAMockServer.stop();
		serviceBMockServer.stop();
		MOCKED_STATIC.close();
		clientUtils.close();
	}

	/**
	 * <pre>
	 *      - service-a is present in namespace a with exposed port 8888
	 *      - service-b is present in namespace b with exposed port 8889
	 *      - we make two calls to them via the load balancer
	 * </pre>
	 */
	@Test
	void test() {

		serviceAMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
			.willReturn(WireMock.aResponse().withBody("service-a-reached").withStatus(200)));

		serviceBMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
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
			.getIfAvailable()
			.getProvider("service-a", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplier.getDelegate().getClass()).isSameAs(KubernetesClientServicesListSupplier.class);

	}

	private static void mockWatchers() {
		V1Service serviceA = Util.service("a", "service-a", SERVICE_A_PORT);
		V1ServiceList serviceListA = new V1ServiceListBuilder().withKind("V1ServiceList")
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(serviceA)
			.build();
		Util.servicesServiceMode(wireMockServer, serviceListA, "service-a");

		V1Service serviceB = Util.service("b", "service-b", SERVICE_B_PORT);
		V1ServiceList serviceListB = new V1ServiceListBuilder().withKind("V1ServiceList")
			.withMetadata(new V1ListMetaBuilder().withResourceVersion("0").build())
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(serviceB)
			.build();
		Util.servicesServiceMode(wireMockServer, serviceListB, "service-b");

		V1Endpoints endpointsA = Util.endpoints("a", "service-a", SERVICE_A_PORT, "127.0.0.1");
		V1EndpointsList endpointsListA = new V1EndpointsListBuilder().withKind("V1EndpointsList")
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpointsA)
			.build();
		Util.endpointsServiceMode(wireMockServer, endpointsListA, "service-a");

		V1Endpoints endpointsB = Util.endpoints("b", "service-b", SERVICE_B_PORT, "127.0.0.1");
		V1EndpointsList endpointsListB = new V1EndpointsListBuilder().withKind("V1EndpointsList")
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpointsB)
			.build();
		Util.endpointsServiceMode(wireMockServer, endpointsListB, "service-b");
	}

}
