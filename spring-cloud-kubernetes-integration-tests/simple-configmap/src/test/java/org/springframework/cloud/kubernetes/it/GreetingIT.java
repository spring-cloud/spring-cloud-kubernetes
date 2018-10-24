package org.springframework.cloud.kubernetes.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.Is.is;

import org.arquillian.cube.kubernetes.annotations.KubernetesResource;
import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@RequiresKubernetes
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GreetingIT {

	private static final String HOST = System.getProperty("service.host");
	private static final Integer PORT = Integer.valueOf(System.getProperty("service.port"));

	@Test
	public void firstTestThatTheDefaultMessageIsReturned() {
		given()
			.baseUri(String.format("http://%s:%d", HOST, PORT))
			.get("greeting")
			.then()
			.statusCode(200)
			.body("message", is("This is a dummy message"));
	}

	@Test
	@KubernetesResource("classpath:config-map.yml")
	public void thenApplyAConfigMapAndEnsureThatTheMessageIsUpdated() {
		waitForApplicationToReload();

		given()
			.baseUri(String.format("http://%s:%d", HOST, PORT))
			.get("greeting")
			.then()
			.statusCode(200)
			.body("message", is("Hello from Spring Cloud Kubernetes!"));
	}

	private void waitForApplicationToReload() {
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
		}
	}

}
