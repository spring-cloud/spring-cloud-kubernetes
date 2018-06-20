package org.springframework.cloud.kubernetes.config;

import static io.restassured.RestAssured.when;
import static org.hamcrest.core.Is.is;
import static org.springframework.cloud.kubernetes.config.ConfigMapTestUtil.readResourceFile;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.restassured.RestAssured;
import java.util.HashMap;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.config.example.App;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = App.class,
	properties = { "spring.application.name=configmap-without-profile-example",
		"spring.cloud.kubernetes.reload.enabled=false"}
)
@ActiveProfiles("development")
public class ConfigMapsWithoutProfilesSpringBootTest {

	@ClassRule
	public static KubernetesServer server = new KubernetesServer();

	private static KubernetesClient mockClient;

	@Autowired(required = false)
	Config config;

	private static final String APPLICATION_NAME = "configmap-without-profile-example";

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

		HashMap<String,String> data = new HashMap<>();
		data.put("application.yml", readResourceFile("application-without-profiles.yaml"));
		server.expect().withPath("/api/v1/namespaces/test/configmaps/" + APPLICATION_NAME).andReturn(200, new ConfigMapBuilder()
			.withNewMetadata().withName(APPLICATION_NAME).endMetadata()
			.addToData(data)
			.build())
			.always();
	}

	@Test
	public void testGreetingEndpoint() {
		RestAssured.baseURI = String.format("http://localhost:%d/api/greeting", port);
		when().get()
			.then()
			.statusCode(200)
			.body("content", is("Hello ConfigMap, World!"));
	}

	@Test
	public void testFarewellEndpoint() {
		RestAssured.baseURI = String.format("http://localhost:%d/api/farewell", port);
		when().get()
			.then()
			.statusCode(200)
			.body("content", is("Goodbye ConfigMap, World!"));
	}
}
