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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.kubernetes.commons.loadbalancer.KubernetesServiceInstanceMapper;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.App;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.TestSocketUtils;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
@SpringBootTest(properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
		"spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.discovery.all-namespaces=false",
		"spring.cloud.kubernetes.client.namespace=a", "spring.cloud.loadbalancer.cache.enabled=false" },
		classes = App.class)
@DirtiesContext
class CacheDisabledTest {

	private static final int SERVICE_PORT = TestSocketUtils.findAvailableTcpPort();

	private static WireMockServer wireMockServer;

	private static WireMockServer serviceAMockServer;

	@SuppressWarnings("rawtypes")
	private static final MockedStatic<KubernetesServiceInstanceMapper> MOCKED_STATIC = Mockito
		.mockStatic(KubernetesServiceInstanceMapper.class);

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@BeforeAll
	static void beforeAll() {

		wireMockServer = new WireMockServer(options().dynamicPort());
		wireMockServer.start();
		WireMock.configureFor("localhost", wireMockServer.port());

		serviceAMockServer = new WireMockServer(SERVICE_PORT);
		serviceAMockServer.start();
		WireMock.configureFor("localhost", SERVICE_PORT);

		// we mock host creation so that it becomes something like : localhost:<port>
		// then wiremock can catch this request, and we can assert for the result
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("my-service", "a", "cluster.local"))
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
		MOCKED_STATIC.close();
	}

	/**
	 * <pre>
	 *      - we disable caching via 'spring.cloud.loadbalancer.cache.enabled=false'
	 *      - as such, two calls to : loadBalancer.choose() will both execute
	 *        on the delegate itself, which we assert via 'wireMockServer.verify'
	 * </pre>
	 */
	@Test
	void test() {

		Service serviceA = Util.service("a", "my-service", SERVICE_PORT);
		String serviceAJson = Serialization.asJson(serviceA);

		wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/namespaces/a/services/service-a"))
			.willReturn(WireMock.aResponse().withBody(serviceAJson).withStatus(200)));

		ReactiveLoadBalancer<ServiceInstance> loadBalancer = loadBalancerClientFactory.getInstance("service-a");
		Response<ServiceInstance> firstResponse = Mono.from(loadBalancer.choose()).block();
		assertThat(firstResponse.hasServer()).isTrue();

		Response<ServiceInstance> secondResponse = Mono.from(loadBalancer.choose()).block();
		assertThat(secondResponse.hasServer()).isTrue();

		// called two times, since caching is disabled.
		wireMockServer.verify(WireMock.exactly(2),
				WireMock.getRequestedFor(WireMock.urlEqualTo("/api/v1/namespaces/a/services/service-a")));

	}

}
