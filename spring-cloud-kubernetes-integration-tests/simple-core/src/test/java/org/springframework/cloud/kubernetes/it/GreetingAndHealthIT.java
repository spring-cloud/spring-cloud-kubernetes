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

package org.springframework.cloud.kubernetes.it;

import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.core.Is.is;

@RequiresKubernetes
@RunWith(Arquillian.class)
public class GreetingAndHealthIT {

	private static final String HOST = System.getProperty("service.host");

	private static final Integer PORT = Integer.valueOf(System.getProperty("service.port"));

	private static final String PROTOCOL = "true".equalsIgnoreCase(System.getProperty("service.secure")) ? "https"
			: "http";

	@Test
	public void testGreetingEndpoint() {
		given().baseUri(String.format("%s://%s:%d", PROTOCOL, HOST, PORT)).get("greeting").then().statusCode(200)
				.body("message", is("Hello from k8s"));
	}

	@Test
	public void testHealthEndpoint() {
		given().baseUri(String.format("%s://%s:%d", PROTOCOL, HOST, PORT)).contentType("application/json")
				.get("actuator/health").then().statusCode(200).body("components.kubernetes.details.inside", is(true));
	}

}
