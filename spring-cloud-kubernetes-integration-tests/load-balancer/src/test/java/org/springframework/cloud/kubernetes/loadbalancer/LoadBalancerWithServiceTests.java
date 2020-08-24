package org.springframework.cloud.kubernetes.loadbalancer;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.model.RequestFieldMatcher;
import io.specto.hoverfly.junit5.HoverflyExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.HttpBodyConverter.json;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
	"spring.cloud.kubernetes.loadbalancer.mode=SERVICE",
	"spring.cloud.kubernetes.loadbalancer.enabled=true"
})
@EnableKubernetesMockClient
@ExtendWith(HoverflyExtension.class)
public class LoadBalancerWithServiceTests {

	@Autowired
	RestTemplate restTemplate;

	static KubernetesClient client;

	@BeforeAll
	public static void setup() {
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
			client.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY,
			"false");
		System.setProperty(Config.KUBERNETES_HTTP2_DISABLE, "true");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
	}

	@Test
	public void testLoadBalancerInServiceMode(Hoverfly hoverfly) {
		hoverfly.simulate(
			dsl(service("http://service-a.test.svc.cluster.local:8080")
				.get("/greeting")
				.willReturn(success().body("greeting"))),
			dsl(service(RequestFieldMatcher.newRegexMatcher("(kubernetes.docker.internal).*"))
				.post("/api/v1/namespaces/test/services").anyBody()
				.willReturn(success()
					.body(json(buildService("service-a", 8080, "test"))))
				.get("/api/v1/namespaces/test/services/service-a")
				.willReturn(success()
					.body(json(buildService("service-a", 8080, "test"))))));
		String response = restTemplate.getForObject("http://service-a/greeting",
			String.class);
		Assertions.assertNotNull(response);
		Assertions.assertEquals("greeting", response);
	}

	private Service buildService(String name, int port, String namespace) {
		return new ServiceBuilder()
			.withNewMetadata()
			.withName(name)
			.withNamespace(namespace)
			.withLabels(new HashMap<>())
			.withAnnotations(new HashMap<>())
			.endMetadata()
			.withNewSpec()
			.addNewPort()
			.withPort(port)
			.endPort()
			.endSpec()
			.build();
	}

}
