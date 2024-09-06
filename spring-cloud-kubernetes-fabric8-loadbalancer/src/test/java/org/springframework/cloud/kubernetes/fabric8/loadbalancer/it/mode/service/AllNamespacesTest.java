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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServicesListSupplier;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
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
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=true" },
		classes = { LoadBalancerConfiguration.class, Configuration.class })
@ExtendWith(OutputCaptureExtension.class)
class AllNamespacesTest {

	private static final String SERVICE_A_URL = "http://service-a";

	private static final String SERVICE_B_URL = "http://service-b";

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
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("service-a", "a", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("service-b", "b", "cluster.local"))
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
	 *      - service-a is present in namespace a with exposed port 8888
	 *      - service-b is present in namespace b with exposed port 8889
	 *      - we make two calls to them via the load balancer
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		Service serviceA = Util.service("a", "service-a", SERVICE_A_PORT);
		Service serviceB = Util.service("b", "service-b", SERVICE_B_PORT);

		String serviceListAJson = Serialization.asJson(new ServiceListBuilder().withItems(serviceA).build());
		String serviceListBJson = Serialization.asJson(new ServiceListBuilder().withItems(serviceB).build());

		wireMockServer
			.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3Dservice-a"))
				.willReturn(WireMock.aResponse().withBody(serviceListAJson).withStatus(200)));

		wireMockServer
			.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3Dservice-b"))
				.willReturn(WireMock.aResponse().withBody(serviceListBJson).withStatus(200)));

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

		CachingServiceInstanceListSupplier supplierA = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getIfAvailable()
			.getProvider("service-a", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplierA.getDelegate().getClass()).isSameAs(Fabric8ServicesListSupplier.class);

		CachingServiceInstanceListSupplier supplierB = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getIfAvailable()
			.getProvider("service-b", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplierB.getDelegate().getClass()).isSameAs(Fabric8ServicesListSupplier.class);

		Assertions.assertThat(output.getOut()).contains("serviceID : service-a");
		Assertions.assertThat(output.getOut()).contains("serviceID : service-b");
		Assertions.assertThat(output.getOut()).contains("discovering services in all namespaces");

		wireMockServer.verify(WireMock.exactly(1), WireMock
			.getRequestedFor(WireMock.urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3Dservice-a")));

		wireMockServer.verify(WireMock.exactly(1), WireMock
			.getRequestedFor(WireMock.urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3Dservice-b")));
	}

}
