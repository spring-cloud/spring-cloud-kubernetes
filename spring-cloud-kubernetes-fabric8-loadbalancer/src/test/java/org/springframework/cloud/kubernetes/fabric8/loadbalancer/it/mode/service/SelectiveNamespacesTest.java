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
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
		"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.all-namespaces=false",
		"spring.cloud.kubernetes.discovery.namespaces.[0]=a", "spring.cloud.kubernetes.discovery.namespaces.[1]=b" },
		classes = { LoadBalancerConfiguration.class, Configuration.class })
@ExtendWith(OutputCaptureExtension.class)
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
		serviceCMockServer.stop();
		MOCKED_STATIC.close();
	}

	/**
	 * <pre>
	 *      - my-service is present in 'a' namespace
	 *      - my-service is present in 'b' namespace
	 *      - my-service is present in 'c' namespace
	 *      - we enable search in selective namespaces [a, b]
	 *      - load balancer mode is 'SERVICE'
	 *
	 *      - as such, only service in namespace a and b are load balanced
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the SERVICE mode.
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		Service serviceA = Util.service("a", "my-service", SERVICE_A_PORT);
		Service serviceB = Util.service("b", "my-service", SERVICE_B_PORT);
		Service serviceC = Util.service("c", "my-service", SERVICE_C_PORT);

		String serviceAJson = Serialization.asJson(serviceA);
		String serviceBJson = Serialization.asJson(serviceB);
		String serviceCJson = Serialization.asJson(serviceC);

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/namespaces/a/services/my-service"))
			.willReturn(WireMock.aResponse().withBody(serviceAJson).withStatus(200)));

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/namespaces/b/services/my-service"))
			.willReturn(WireMock.aResponse().withBody(serviceBJson).withStatus(200)));

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/namespaces/c/services/my-service"))
			.willReturn(WireMock.aResponse().withBody(serviceCJson).withStatus(200)));

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
		Assertions.assertThat(supplier.getDelegate().getClass()).isSameAs(Fabric8ServicesListSupplier.class);

		Assertions.assertThat(output.getOut()).contains("serviceID : my-service");
		Assertions.assertThat(output.getOut()).contains("discovering services in selective namespaces : [a, b]");

		wireMockServer.verify(WireMock.exactly(1),
				WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/namespaces/a/services/my-service")));

		wireMockServer.verify(WireMock.exactly(1),
				WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/namespaces/b/services/my-service")));

		// not triggered in namespace 'c' since that is not a selective namespace
		wireMockServer.verify(WireMock.exactly(0),
				WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/namespaces/c/services/my-service")));
	}

}
