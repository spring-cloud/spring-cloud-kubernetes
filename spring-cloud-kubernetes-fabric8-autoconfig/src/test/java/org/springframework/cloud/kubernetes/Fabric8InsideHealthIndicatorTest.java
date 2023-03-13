/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.Collections;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.cloud.kubernetes.example.App;
import org.springframework.cloud.kubernetes.fabric8.Fabric8HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author wind57
 *
 * test to see if proper fields are set in health when it is running inside the container
 */
@Import(Fabric8InsideHealthIndicatorTest.KubernetesActuatorTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "management.endpoint.health.show-details=always" })
class Fabric8InsideHealthIndicatorTest {

	@Autowired
	private WebTestClient webClient;

	@LocalManagementPort
	private int port;

	/**
	 * <pre>
	 * "stubKubernetes": {
	 *       "status": "UP",
	 *       "details": {
	 *         "nodeName": "nodeName",
	 *         "podIp": "10.1.1.1",
	 *         "hostIp": "192.168.10.3",
	 *         "namespace": "namespace",
	 *         "podName": "pod",
	 *         "serviceAccount": "serviceAccountName",
	 *         "inside": true,
	 *         "labels": {
	 *           "labelName": "labelValue"
	 *         }
	 *       }
	 *  </pre>
	 */
	@Test
	void test() {
		this.webClient.get().uri("http://localhost:{port}/actuator/health", this.port)
				.accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody()
				.jsonPath("components.stubKubernetes.status").isEqualTo("UP")
				.jsonPath("components.stubKubernetes.details.nodeName").isEqualTo("nodeName")
				.jsonPath("components.stubKubernetes.details.podIp").isEqualTo("10.1.1.1")
				.jsonPath("components.stubKubernetes.details.hostIp").isEqualTo("192.160.10.3")
				.jsonPath("components.stubKubernetes.details.namespace").isEqualTo("namespace")
				.jsonPath("components.stubKubernetes.details.podName").isEqualTo("pod")
				.jsonPath("components.stubKubernetes.details.serviceAccount").isEqualTo("serviceAccountName")
				.jsonPath("components.stubKubernetes.details.inside").isEqualTo("true")
				.jsonPath("components.stubKubernetes.details.labels.labelName").isEqualTo("labelValue");
	}

	private static Pod stubPod() {

		PodStatus status = new PodStatus();
		status.setPodIP("10.1.1.1");
		status.setHostIP("192.160.10.3");

		PodSpec spec = new PodSpec();
		spec.setServiceAccountName("serviceAccountName");
		spec.setNodeName("nodeName");

		return new PodBuilder().withNewMetadata().withName("pod").withNamespace("namespace")
				.withLabels(Collections.singletonMap("labelName", "labelValue")).endMetadata().withStatus(status)
				.withSpec(spec).build();
	}

	@Configuration
	static class KubernetesActuatorTestConfiguration {

		@Primary
		@Bean
		public Fabric8HealthIndicator stubKubernetesHealthIndicator() {
			@SuppressWarnings("unchecked")
			PodUtils<Pod> utils = Mockito.mock(PodUtils.class);
			Mockito.when(utils.currentPod()).thenReturn(Fabric8InsideHealthIndicatorTest::stubPod);
			return new Fabric8HealthIndicator(utils);
		}

	}

}
