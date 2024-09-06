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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.pod;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util.Configuration;
import static org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util.LoadBalancerConfiguration;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=POD", "spring.main.cloud-platform=KUBERNETES",
		"spring.cloud.kubernetes.discovery.all-namespaces=false", "spring.cloud.kubernetes.client.namespace=a" },
		classes = { LoadBalancerConfiguration.class, Configuration.class })
class SpecificNamespaceTest {

	private static final String SERVICE_A_URL = "http://my-service";

	private static final int SERVICE_A_PORT = 8888;

	private static final int SERVICE_B_PORT = 8889;

	private static WireMockServer wireMockServer;

	private static WireMockServer serviceAMockServer;

	private static WireMockServer serviceBMockServer;

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

		serviceAMockServer = new WireMockServer(SERVICE_A_PORT);
		serviceAMockServer.start();
		WireMock.configureFor("localhost", SERVICE_A_PORT);

		serviceBMockServer = new WireMockServer(SERVICE_B_PORT);
		serviceBMockServer.start();
		WireMock.configureFor("localhost", SERVICE_B_PORT);

		// we mock host creation so that it becomes something like : localhost:8888
		// then wiremock can catch this request, and we can assert for the result
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "a", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "b", "cluster.local"))
			.thenReturn("localhost");

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, "http://localhost:" + wireMockServer.port());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
	}

	@AfterAll
	static void afterAll() {
		wireMockServer.stop();
		serviceAMockServer.stop();
		serviceBMockServer.stop();
		MOCKED_STATIC.close();
	}

	/**
	 * <pre>
	 *      - my-service is present in 'a' namespace
	 *      - my-service is present in 'b' namespace
	 *      - we enable search in namespace 'a'
	 *      - load balancer mode is 'POD'
	 *
	 *      - as such, only my-service in namespace a is load balanced
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the POD mode.
	 * </pre>
	 */
	@Test
	void test() {

		Service serviceA = Util.service("a", "my-service", SERVICE_A_PORT);
		Service serviceB = Util.service("b", "my-service", SERVICE_B_PORT);

		Endpoints endpointsA = Util.endpoints(SERVICE_A_PORT, "127.0.0.1", "a");
		Endpoints endpointsB = Util.endpoints(SERVICE_B_PORT, "127.0.0.1", "b");

		String endpointsAListAsString = Serialization.asJson(new EndpointsListBuilder().withItems(endpointsA).build());
		String endpointsBListAsString = Serialization.asJson(new EndpointsListBuilder().withItems(endpointsB).build());

		String serviceAString = Serialization.asJson(serviceA);
		String serviceBString = Serialization.asJson(serviceB);

		wireMockServer.stubFor(WireMock
			.get(WireMock.urlEqualTo("/api/v1/namespaces/a/endpoints?fieldSelector=metadata.name%3Dmy-service"))
			.willReturn(WireMock.aResponse().withBody(endpointsAListAsString).withStatus(200)));

		wireMockServer.stubFor(WireMock
			.get(WireMock.urlEqualTo("/api/v1/namespaces/b/endpoints?fieldSelector=metadata.name%3Dmy-service"))
			.willReturn(WireMock.aResponse().withBody(endpointsBListAsString).withStatus(200)));

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/namespaces/a/services/my-service"))
			.willReturn(WireMock.aResponse().withBody(serviceAString).withStatus(200)));

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/namespaces/b/services/my-service"))
			.willReturn(WireMock.aResponse().withBody(serviceBString).withStatus(200)));

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

		CachingServiceInstanceListSupplier supplier = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getIfAvailable()
			.getProvider("my-service", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplier.getDelegate().getClass())
			.isSameAs(DiscoveryClientServiceInstanceListSupplier.class);

		wireMockServer.verify(WireMock.exactly(1), WireMock.getRequestedFor(
				WireMock.urlEqualTo("/api/v1/namespaces/a/endpoints?fieldSelector=metadata.name%3Dmy-service")));

		wireMockServer.verify(WireMock.exactly(0), WireMock.getRequestedFor(
				WireMock.urlEqualTo("/api/v1/namespaces/b/endpoints?fieldSelector=metadata.name%3Dmy-service")));

		wireMockServer.verify(WireMock.exactly(1),
				WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/namespaces/a/services/my-service")));

		wireMockServer.verify(WireMock.exactly(0),
				WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/namespaces/b/services/me-service")));
	}

}
