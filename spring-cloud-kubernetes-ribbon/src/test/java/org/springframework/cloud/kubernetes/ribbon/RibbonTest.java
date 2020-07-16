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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.EndpointsBuilder;
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
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Charles Moulliard
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class,
		properties = { "spring.application.name=testapp",
				"spring.cloud.kubernetes.client.namespace=testns",
				"spring.cloud.kubernetes.client.trustCerts=true",
				"spring.cloud.kubernetes.config.namespace=testns" })
@EnableAutoConfiguration
@EnableDiscoveryClient
public class RibbonTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	@ClassRule
	public static KubernetesServer mockEndpointA = new KubernetesServer(false);

	@ClassRule
	public static KubernetesServer mockEndpointB = new KubernetesServer(false);

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
		server.expect().get().withPath("/api/v1/namespaces/testns/endpoints/testapp")
				.andReturn(200, new EndpointsBuilder().withNewMetadata()
						.withName("testapp-a").endMetadata().addNewSubset()
						.addNewAddress()
						.withIp(mockEndpointA.getMockServer().getHostName()).endAddress()
						.addNewPort("http", "http_tcp",
								mockEndpointA.getMockServer().getPort(), "http")
						.endSubset().addNewSubset().addNewAddress()
						.withIp(mockEndpointB.getMockServer().getHostName()).endAddress()
						.addNewPort("http", "http_tcp",
								mockEndpointB.getMockServer().getPort(), "http")
						.endSubset().build())
				.always();

		mockEndpointA.expect().get().withPath("/greeting").andReturn(200, "Hello from A")
				.always();
		mockEndpointB.expect().get().withPath("/greeting").andReturn(200, "Hello from B")
				.always();
	}

	@Test
	public void testGreetingEndpoint() {
		final List<String> greetings = new ArrayList<>();
		greetings.add(
				this.restTemplate.getForObject("http://testapp/greeting", String.class));
		greetings.add(
				this.restTemplate.getForObject("http://testapp/greeting", String.class));

		assertThat(greetings).containsOnly("Hello from A", "Hello from B");
	}

}
