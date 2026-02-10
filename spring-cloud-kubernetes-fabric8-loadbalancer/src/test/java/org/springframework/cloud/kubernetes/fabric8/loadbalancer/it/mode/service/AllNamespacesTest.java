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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.service;

import java.util.Map;

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
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServicesListSupplier;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.Util;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.App;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.it.mode.LoadBalancerConfiguration;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
@SpringBootTest(
		properties = { "spring.cloud.kubernetes.loadbalancer.mode=SERVICE", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.kubernetes.discovery.all-namespaces=true" },
		classes = { LoadBalancerConfiguration.class, App.class })
@ExtendWith(OutputCaptureExtension.class)
@EnableKubernetesMockClient(https = false)
class AllNamespacesTest {

	private static KubernetesMockServer kubernetesMockServer;

	private static final String SERVICE_A_URL = "http://service-a/a-path";

	private static final String SERVICE_B_URL = "http://service-b/b-path";

	@Autowired
	private WebClient.Builder builder;

	@Autowired
	private LoadBalancerClientFactory loadBalancerClientFactory;

	@SuppressWarnings("rawtypes")
	private static final MockedStatic<KubernetesServiceInstanceMapper> MOCKED_STATIC = Mockito
		.mockStatic(KubernetesServiceInstanceMapper.class);

	@BeforeAll
	static void beforeAll() {

		// we mock host creation so that it becomes something like : localhost:<port>
		// then wiremock can catch this request, and we can assert for the result
		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("service-a", "a", "cluster.local"))
			.thenReturn("localhost");

		MOCKED_STATIC.when(() -> KubernetesServiceInstanceMapper.createHost("service-b", "b", "cluster.local"))
			.thenReturn("localhost");

		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, kubernetesMockServer.url("/"));
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");

		Util.mockAllNamespacesIndexerServiceCalls(Map.of("a", "service-a", "b", "service-b"), kubernetesMockServer);
		Util.mockAllNamespacesIndexerEndpointsCalls(Map.of("a", "service-a", "b", "service-b"), "localhost",
				kubernetesMockServer.getPort(), kubernetesMockServer);
		Util.mockLoadBalancerMetadataNameServiceCallInAllNamespaces("a", "service-a", kubernetesMockServer,
				kubernetesMockServer.getPort(), "http", 1);
		Util.mockLoadBalancerMetadataNameServiceCallInAllNamespaces("b", "service-b", kubernetesMockServer,
				kubernetesMockServer.getPort(), "http", 1);

		kubernetesMockServer.expect().get().withPath("/a-path").andReturn(200, "service-a-reached").once();

		kubernetesMockServer.expect().get().withPath("/b-path").andReturn(200, "service-b-reached").once();

	}

	@AfterAll
	static void afterAll() {
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
			.getProvider("service-a", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplierA.getDelegate().getClass()).isSameAs(Fabric8ServicesListSupplier.class);

		CachingServiceInstanceListSupplier supplierB = (CachingServiceInstanceListSupplier) loadBalancerClientFactory
			.getProvider("service-b", ServiceInstanceListSupplier.class)
			.getIfAvailable();
		Assertions.assertThat(supplierB.getDelegate().getClass()).isSameAs(Fabric8ServicesListSupplier.class);

		Assertions.assertThat(output.getOut()).contains("serviceID : service-a");
		Assertions.assertThat(output.getOut()).contains("serviceID : service-b");
		Assertions.assertThat(output.getOut()).contains("discovering services in all namespaces");

	}

}
