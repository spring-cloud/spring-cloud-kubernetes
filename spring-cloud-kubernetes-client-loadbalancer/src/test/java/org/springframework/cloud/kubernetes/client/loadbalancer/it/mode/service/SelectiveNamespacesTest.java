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
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
		"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.all-namespaces=false",
		"spring.cloud.kubernetes.discovery.namespaces.[0]=a", "spring.cloud.kubernetes.discovery.namespaces.[1]=b" },
		classes = { LoadBalancerConfiguration.class, Configuration.class })
class SelectiveNamespacesTest {

	private static final String MY_SERVICE_URL = "http://my-service";

	private static final int SERVICE_A_PORT = 8887;

	private static final int SERVICE_B_PORT = 8888;

	private static final int SERVICE_C_PORT = 8889;

	private static WireMockServer wireMockServer;

	private static WireMockServer serviceAMockServer;

	private static WireMockServer serviceBMockServer;

	private static WireMockServer serviceCMockServer;

	private static final MockedStatic<KubernetesServiceInstanceMapper> MOCKED_STATIC = Mockito
		.mockStatic(KubernetesServiceInstanceMapper.class);

	private static MockedStatic<KubernetesClientUtils> clientUtils;

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

		serviceCMockServer = new WireMockServer(SERVICE_C_PORT);
		serviceCMockServer.start();
		WireMock.configureFor("localhost", SERVICE_C_PORT);

		// we mock host creation so that it becomes something like : localhost:8888
		// then wiremock can catch this request, and we can assert for the result
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "a", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "b", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "c", "cluster.local"))
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
		serviceCMockServer.stop();
		MOCKED_STATIC.close();
		clientUtils.close();
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

		String firstCallResult = builder.baseUrl(MY_SERVICE_URL)
			.build()
			.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.block();

		String secondCallResult = builder.baseUrl(MY_SERVICE_URL)
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
			.getIfAvailable()
			.getProvider("my-service", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplier.getDelegate().getClass()).isSameAs(KubernetesClientServicesListSupplier.class);
	}

	private static void mockWatchers() {
		V1Service serviceA = Util.service("a", "my-service", SERVICE_A_PORT);
		V1Service serviceB = Util.service("b", "my-service", SERVICE_B_PORT);
		V1Service serviceC = Util.service("c", "my-service", SERVICE_C_PORT);

		V1ServiceList serviceListA = new V1ServiceListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(serviceA)
			.build();
		V1ServiceList serviceListB = new V1ServiceListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(serviceB)
			.build();
		V1ServiceList serviceListC = new V1ServiceListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(serviceC)
			.build();

		Util.servicesInNamespaceServiceMode(wireMockServer, serviceListA, "a", "my-service");
		Util.servicesInNamespaceServiceMode(wireMockServer, serviceListB, "b", "my-service");
		Util.servicesInNamespaceServiceMode(wireMockServer, serviceListC, "c", "my-service");

		V1Endpoints endpointsA = Util.endpoints("a", "my-service", SERVICE_A_PORT, "127.0.0.1");
		V1Endpoints endpointsB = Util.endpoints("b", "my-service", SERVICE_B_PORT, "127.0.0.1");
		V1Endpoints endpointsC = Util.endpoints("c", "my-service", SERVICE_C_PORT, "127.0.0.1");

		V1EndpointsList endpointsListA = new V1EndpointsListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpointsA)
			.build();
		V1EndpointsList endpointsListB = new V1EndpointsListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpointsB)
			.build();
		V1EndpointsList endpointsListC = new V1EndpointsListBuilder()
			.withNewMetadataLike(new V1ListMetaBuilder().withResourceVersion("0").build())
			.endMetadata()
			.withItems(endpointsC)
			.build();

		Util.endpointsInNamespaceServiceMode(wireMockServer, endpointsListA, "a", "my-service");
		Util.endpointsInNamespaceServiceMode(wireMockServer, endpointsListB, "b", "my-service");
		Util.endpointsInNamespaceServiceMode(wireMockServer, endpointsListC, "c", "my-service");
	}

}
