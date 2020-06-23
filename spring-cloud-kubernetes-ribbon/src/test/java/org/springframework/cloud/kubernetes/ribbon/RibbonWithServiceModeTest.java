/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.ribbon;

import java.util.List;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * the RibbonWithServiceModeTest description.
 *
 * @author wuzishu
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class,
		properties = { "spring.application.name=testapp",
				"spring.cloud.kubernetes.client.namespace=testns",
				"spring.cloud.kubernetes.client.trustCerts=true",
				"spring.cloud.kubernetes.config.namespace=testns",
				"spring.cloud.kubernetes.enabled=true",
				"spring.cloud.kubernetes.discovery.enabled=true",
				"spring.cloud.kubernetes.ribbon.enabled=true",
				"spring.cloud.kubernetes.ribbon.mode=SERVICE",
				"spring.cloud.kubernetes.ribbon.clusterDomain=test.com" })
@EnableAutoConfiguration
@EnableDiscoveryClient
public class RibbonWithServiceModeTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	@ClassRule
	public static KubernetesServer mockEndpointA = new KubernetesServer(false);

	private static KubernetesClient mockClient;

	@Autowired
	private RestTemplate restTemplate;

	@BeforeClass
	public static void setUpBefore() {
		mockClient = server.getClient();

		// Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
				mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
				"false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");

		// Configured
		server.expect().get().withPath("/api/v1/namespaces/testns/services/testapp")
				.andReturn(200, new ServiceBuilder().withNewMetadata().withName("testapp")
						.withNamespace("testns").endMetadata().withNewSpec()
						.addToSelector("app", "testapp-a").addNewPort().withName("http")
						.withPort(mockEndpointA.getMockServer().getPort())
						.withTargetPort(
								new IntOrString(mockEndpointA.getMockServer().getPort()))
						.withProtocol("TCP").endPort().endSpec().build())
				.always();

	}

	@Autowired
	private ApplicationContext context;

	@Test
	public void testGreetingWithServiceMode() {
		SpringClientFactory springClientFactory = context
				.getBean(SpringClientFactory.class);
		ILoadBalancer testapp = springClientFactory.getLoadBalancer("testapp");
		List<Server> allServers = testapp.getAllServers();
		assertThat(allServers.stream()
				.map(c -> String.format("%s:%s", c.getHost(), c.getPort())))
						.containsOnly("testapp.testns.svc.test.com:"
								+ mockEndpointA.getMockServer().getPort());
	}

}
