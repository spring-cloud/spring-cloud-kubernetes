package org.springframework.kubernetes.loadbalancer;

import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.spring.framework.kubernetes.loadbalancer.SimpleLoadBalancerApp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(classes = {SimpleLoadBalancerApp.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class LoadBalancerTest {

	@Autowired
	RestTemplate restTemplate;
	@ClassRule
	public static KubernetesServer server = new KubernetesServer(true, true);

	private static KubernetesClient client;

	@BeforeClass
	public static void setup() {
		client = server.getClient();

		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, client.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
	}

	@Test
	public void testLoadBalancerSameNamespace() {
		createTestData("service-a", "test");
		String response = restTemplate.getForObject("http://service-a/greeting", String.class);
		Assertions.assertNotNull(response);
		Assertions.assertEquals("greeting", response);
	}

	@Test
	public void testLoadBalancerDifferentNamespace() {
		createTestData("service-b", "b");
		Assertions.assertThrows(IllegalStateException.class, () -> restTemplate
			.getForObject("http://service-b/greeting", String.class));
	}

	private void createTestData(String name, String namespace) {
		client.services().inNamespace(namespace).createNew()
			.withNewMetadata().withName(name).endMetadata()
			.withSpec(new ServiceSpecBuilder()
				.withPorts(new ServicePortBuilder().withProtocol("TCP").withPort(8080).build())
				.build())
			.done();
		client.endpoints().inNamespace(namespace).createNew()
			.withNewMetadata().withName("service-a").endMetadata()
			.addNewSubset()
			.addNewAddress().withIp("localhost").endAddress()
			.addNewPort().withName("http").withPort(8080).endPort()
			.endSubset()
			.done();
	}

}
