/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.leader.election;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.builder;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.retrySpec;

/**
 * @author wind57
 */
class Fabric8LeaderElectionTest {

	private static final String NAMESPACE = "default";

	private static final String FABRIC8_LEADER_APP_A = "fabric8-leader-app";

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(Fabric8LeaderElectionTest.class);

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		util = new Util(K3S);

		Commons.validateImage(FABRIC8_LEADER_APP_A, K3S);
		Commons.loadSpringCloudKubernetesImage(FABRIC8_LEADER_APP_A, K3S);

		util.setUp(NAMESPACE);
	}

	@BeforeEach
	void beforeEach() {
		appA(Phase.CREATE);
		appB(Phase.CREATE);
	}

	@AfterEach
	void afterEach() {
		// appA is deleted within the test itself
		appB(Phase.DELETE);
	}

	@Test
	void test() throws Exception {
		WebClient clientA = builder().baseUrl("http://localhost:32321/actuator/info").build();
		WebClient clientB = builder().baseUrl("http://localhost:32322/actuator/info").build();

		String podAInfoResult = clientA.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		String podBInfoResult = clientB.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		// we start podA first, so it will be the leader
		assertThat(BASIC_JSON_TESTER.from(podAInfoResult)).extractingJsonPathBooleanValue("$.leaderElection.isLeader")
			.isEqualTo(true);

		String leaderFromLease = K3S
			.execInContainer("sh", "-c",
					"kubectl get lease spring-k8s-leader-election-lock -o=jsonpath='{.spec.holderIdentity}'")
			.getStdout();
		assertThat(leaderFromLease).contains("fabric8-leader-app-a");

		// podB is a follower
		assertThat(BASIC_JSON_TESTER.from(podBInfoResult)).extractingJsonPathBooleanValue("$.leaderElection.isLeader")
			.isEqualTo(false);

		String renewTime = K3S
			.execInContainer("sh", "-c",
					"kubectl get lease spring-k8s-leader-election-lock -o=jsonpath='{.spec.renewTime}'")
			.getStdout();

		// lease has renew time changed
		Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
			String localRenewTime = K3S
				.execInContainer("sh", "-c",
						"kubectl get lease spring-k8s-leader-election-lock -o=jsonpath='{.spec.renewTime}'")
				.getStdout();
			assertThat(renewTime).isNotEqualTo(localRenewTime);
		});

		// delete appA and so, appB can become the leader
		appA(Phase.DELETE);

		// lease now shows that appB is the leader
		Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
			String newLeader = K3S
				.execInContainer("sh", "-c",
						"kubectl get lease spring-k8s-leader-election-lock -o=jsonpath='{.spec.holderIdentity}'")
				.getStdout();
			assertThat(newLeader).contains("fabric8-leader-app-b");
		});

		podBInfoResult = clientB.method(HttpMethod.GET)
			.retrieve()
			.bodyToMono(String.class)
			.retryWhen(retrySpec())
			.block();

		// info actuator confirms that podB is the leader
		assertThat(BASIC_JSON_TESTER.from(podBInfoResult)).extractingJsonPathBooleanValue("$.leaderElection.isLeader")
			.isEqualTo(true);

	}

	private void appA(Phase phase) {
		InputStream deploymentStream = util.inputStream("app/deployment.yaml");
		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		InputStream serviceStream = util.inputStream("app/service-a.yaml");
		Service service = Serialization.unmarshal(serviceStream, Service.class);

		// 1. change name
		deployment.getMetadata().setName("fabric8-leader-app-a");

		// 2. change spec.selector.matchLabels
		deployment.getSpec().getSelector().setMatchLabels(Map.of("app", "app-a"));

		// 3. change spec.template.metadata.labels
		deployment.getSpec().getTemplate().getMetadata().setLabels(Map.of("app", "app-a"));

		// 4. change env variables
		List<EnvVar> env = new ArrayList<>();
		env.add(new EnvVarBuilder().withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_LEADER")
			.withValue("DEBUG")
			.build());
		env.add(new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_LEADER_ELECTION_ENABLED")
			.withValue("TRUE")
			.build());
		env.add(new EnvVarBuilder().withName("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED").withValue("TRUE").build());

		env.add(new EnvVarBuilder().withName("SPRING_APPLICATION_NAME").withValue("fabric8-leader-app-a").build());

		env.add(new EnvVarBuilder().withName("MANAGEMENT_HEALTH_LEADER_ELECTION_ENABLED").withValue("TRUE").build());

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(env);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service);
		}
	}

	private void appB(Phase phase) {
		InputStream deploymentStream = util.inputStream("app/deployment.yaml");
		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		InputStream serviceStream = util.inputStream("app/service-b.yaml");
		Service service = Serialization.unmarshal(serviceStream, Service.class);

		// 1. change name
		deployment.getMetadata().setName("fabric8-leader-app-b");

		// 2. change spec.selector.matchLabels
		deployment.getSpec().getSelector().setMatchLabels(Map.of("app", "app-b"));

		// 3. change spec.template.metadata.labels
		deployment.getSpec().getTemplate().getMetadata().setLabels(Map.of("app", "app-b"));

		// 4. change env variables
		List<EnvVar> env = new ArrayList<>();
		env.add(new EnvVarBuilder().withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_LEADER")
			.withValue("DEBUG")
			.build());
		env.add(new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_LEADER_ELECTION_ENABLED")
			.withValue("TRUE")
			.build());
		env.add(new EnvVarBuilder().withName("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED").withValue("TRUE").build());

		env.add(new EnvVarBuilder().withName("SPRING_APPLICATION_NAME").withValue("fabric8-leader-app-b").build());

		env.add(new EnvVarBuilder().withName("MANAGEMENT_HEALTH_LEADER_ELECTION_ENABLED").withValue("TRUE").build());

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(env);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(NAMESPACE, null, deployment, service, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(NAMESPACE, deployment, service);
		}
	}

}
