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

package org.springframework.cloud.kubernetes;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.example.App;
import org.springframework.cloud.kubernetes.fabric8.Fabric8PodUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 *
 * test proper fields being set in /actuator/info
 */
@Import(Fabric8InsideInfoContributorTest.InfoContributorTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "management.endpoints.web.exposure.include=info", "management.endpoint.info.show-details=always",
				"management.info.kubernetes.enabled=true" })
public class Fabric8InsideInfoContributorTest {

	@Autowired
	private WebTestClient webClient;

	@Value("${local.server.port}")
	private int port;

	@Test
	public void test() {
		this.webClient.get().uri("http://localhost:{port}/actuator/info", this.port).accept(MediaType.APPLICATION_JSON)
				.exchange().expectStatus().isOk().expectBody(String.class)
				.value(Fabric8InsideInfoContributorTest::validateInfo);
	}

	private static Pod stubPod() {

		PodStatus status = new PodStatus();
		status.setPodIP("10.1.1.1");
		status.setHostIP("192.160.10.3");

		PodSpec spec = new PodSpec();
		spec.setServiceAccountName("serviceAccountName");
		spec.setNodeName("nodeName");

		return new PodBuilder().withNewMetadata().withName("pod").withNamespace("namespace").endMetadata()
				.withStatus(status).withSpec(spec).build();
	}

	/**
	 * <pre>
	 *   "kubernetes": {
	 *     "nodeName": "nodeName",
	 *     "podIp": "10.1.1.1",
	 *     "hostIp": "192.160.10.3",
	 *     "namespace": "namespace",
	 *     "podName": "pod",
	 *     "serviceAccount": "serviceAccountName",
	 *     "inside": true
	 *   }
	 *  </pre>
	 */
	@SuppressWarnings("unchecked")
	private static void validateInfo(String input) {
		try {
			Map<String, Object> map = new ObjectMapper().readValue(input, new TypeReference<Map<String, Object>>() {

			});
			Map<String, Object> infoProperties = (Map<String, Object>) map.get("kubernetes");

			Assertions.assertEquals("nodeName", infoProperties.get("nodeName"));
			Assertions.assertEquals("10.1.1.1", infoProperties.get("podIp"));
			Assertions.assertEquals("192.160.10.3", infoProperties.get("hostIp"));
			Assertions.assertEquals("namespace", infoProperties.get("namespace"));
			Assertions.assertEquals("pod", infoProperties.get("podName"));
			Assertions.assertEquals("serviceAccountName", infoProperties.get("serviceAccount"));
			Assertions.assertTrue((Boolean) infoProperties.get("inside"));

		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Configuration
	static class InfoContributorTestConfig {

		@Bean
		public Fabric8PodUtils fabric8PodUtils() {
			Fabric8PodUtils utils = Mockito.mock(Fabric8PodUtils.class);
			Mockito.when(utils.currentPod()).thenReturn(Fabric8InsideInfoContributorTest::stubPod);
			return utils;
		}

	}

}
