package org.springframework.cloud.kubernetes.it;

import static io.restassured.RestAssured.given;

import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.hamcrest.core.StringContains;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresKubernetes
@RunWith(Arquillian.class)
public class ProfilesIT {

	private static final String HOST = System.getProperty("service.host");
	private static final Integer PORT = Integer.valueOf(System.getProperty("service.port"));

	@Test
	public void testProfileEndpoint() {
		given()
			.baseUri(String.format("http://%s:%d", HOST, PORT))
			.get("profiles")
			.then()
			.statusCode(200)
			.body(new StringContains("istio"));
	}

}
