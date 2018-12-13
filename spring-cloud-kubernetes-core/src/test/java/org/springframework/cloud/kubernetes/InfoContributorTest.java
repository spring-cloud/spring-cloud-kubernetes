package org.springframework.cloud.kubernetes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.StringContains.containsString;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.server.mock.KubernetesServer;
import io.restassured.RestAssured;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.example.App;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class)
public class InfoContributorTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Value("${local.server.port}")
	private int port;


	@BeforeClass
	public static void setUpBeforeClass() {
		mockClient = server.getClient();

		//Configure the kubernetes master url to point to the mock server
		System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, mockClient.getConfiguration().getMasterUrl());
		System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
		System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
		System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "test");
	}

	@Test
	public void infoEndpointShouldContainKubernetes() {
		RestAssured.baseURI = String.format("http://localhost:%d/actuator/info", port);
		given()
			.contentType("application/json")
			.get()
			.then()
			.statusCode(200)
			.body(containsString("kubernetes"));
	}


}
