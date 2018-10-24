package org.springframework.cloud.kubernetes.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.Is.is;

import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresKubernetes
@RunWith(Arquillian.class)
public class GreetingAndHealthIT {

	private static final String HOST = System.getProperty("service.host");
	private static final Integer PORT = Integer.valueOf(System.getProperty("service.port"));

	@Test
	public void testGreetingEndpoint() {
		given()
			.baseUri(String.format("http://%s:%d", HOST, PORT))
			.get("greeting")
			.then()
			.statusCode(200)
			.body("message", is("Hello Spring Boot"));
	}

	@Test
	public void testHealthEndpoint() {
		given()
			.baseUri(String.format("http://%s:%d", HOST, PORT))
			.contentType("application/json")
			.get("actuator/health")
			.then()
			.statusCode(200)
			.body("details.kubernetes.details.inside",  is(true));
	}

}
