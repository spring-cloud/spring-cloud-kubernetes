package org.springframework.cloud.kubernetes.it;

import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.hamcrest.core.StringContains;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;

@RequiresKubernetes
@RunWith(Arquillian.class)
public class ServicesIT {

	private static final String HOST = System.getProperty("service.host");
	private static final Integer PORT = Integer.valueOf(System.getProperty("service.port"));

	@Test
	public void testServicesEndpoint() {
		given()
			.baseUri(String.format("http://%s:%d", HOST, PORT))
			.get("services")
			.then()
			.statusCode(200)
			.body(new StringContains("service-a") {
                @Override
                protected boolean evalSubstringOf(String s) {
                    return s.contains("service-a") && s.contains("service-b");
                }
            });
	}

}
