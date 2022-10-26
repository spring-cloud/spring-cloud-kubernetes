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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
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
		properties = { "spring.main.cloud-platform=KUBERNETES", "management.endpoints.web.exposure.include=info",
				"management.endpoint.info.show-details=always", "management.info.kubernetes.enabled=true" })
class Fabric8InsideInfoContributorTest {

	@Autowired
	private WebTestClient webClient;

	@LocalManagementPort
	private int port;

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
	@Test
	void test() {
		this.webClient.get().uri("http://localhost:{port}/actuator/info", this.port).accept(MediaType.APPLICATION_JSON)
				.exchange().expectStatus().isOk().expectBody().jsonPath("kubernetes.nodeName").isEqualTo("nodeName")
				.jsonPath("kubernetes.podIp").isEqualTo("10.1.1.1").jsonPath("kubernetes.hostIp")
				.isEqualTo("192.160.10.3").jsonPath("kubernetes.namespace").isEqualTo("namespace")
				.jsonPath("kubernetes.podName").isEqualTo("pod").jsonPath("kubernetes.serviceAccount")
				.isEqualTo("serviceAccountName").jsonPath("kubernetes.inside").isEqualTo("true");

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
