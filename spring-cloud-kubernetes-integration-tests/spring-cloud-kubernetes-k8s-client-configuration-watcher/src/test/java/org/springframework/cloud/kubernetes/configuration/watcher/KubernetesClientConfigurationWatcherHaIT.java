/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.Arrays;
import java.util.List;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the initial Kubernetes-client configuration watcher HA wiring.
 *
 * @author wind57
 */
@NativeClientIntegrationTest(withImages = { "spring-cloud-kubernetes-configuration-watcher" },
		wiremock = @NativeClientIntegrationTest.Wiremock(enabled = true, namespaces = "default", withNodePort = true),
		rbacNamespaces = "default",
		configurationWatcher = @NativeClientIntegrationTest.ConfigurationWatcher(enabled = true, enableHa = true,
				replicas = 2, refreshDelay = "0", reloadEnabled = false))
class KubernetesClientConfigurationWatcherHaIT {

	@BeforeAll
	static void beforeAll(NativeClientKubernetesFixture fixture) {
		TestUtil.configureWireMock(fixture);
		TestUtil.createConfigMap(fixture, "default");
	}

	@AfterAll
	static void afterAll(NativeClientKubernetesFixture fixture) {
		TestUtil.deleteConfigMap(fixture, "default");
	}

	/**
	 * <pre>
	 *     - start two configuration watcher replicas with HA enabled
	 *     - wait until both replicas are running
	 *     - verify that exactly one replica holds the leader-election lease
	 *     - update the ConfigMap once
	 *     - verify that the change triggers exactly one actuator refresh
	 *     - delete the current leader
	 *     - update the ConfigMap while no watcher is leading
	 *     - verify that no actuator refresh is sent before leadership changes
	 *     - verify that the replacement leader refreshes the latest ConfigMap value
	 * </pre>
	 */
	@Test
	void refreshesConfigMapAfterLeaderLoss(K3sContainer container) {

		// 1. we have two replicas running
		// 2. only one is the HA leader
		Awaitilities.awaitUntilAsserted(120, 1000, () -> {
			try {
				// we have two replicas, one is the HA leader
				List<String> runningPods = runningPods(container);
				assertThat(runningPods).hasSize(2);

				String holderIdentity = currentLeaderAccordingToLeaderLease(container);
				assertThat(holderIdentity).isNotBlank();
				assertThat(runningPods).contains(holderIdentity);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		// 3. capture the current resource version before the first update
		String firstResourceVersion = configMapResourceVersion(container);

		// 4. wait for the initial onAdd refresh to complete.
		Awaitilities.awaitUntilAsserted(120, 1000, () -> TestUtil.verifyActuatorCalled(1));
		// ignore initial onAdd refresh
		WireMock.resetAllRequests();

		patchConfigMap(container, "updated");
		String secondResourceVersion = configMapResourceVersion(container);

		// 5. because of the update in the configmap, the watcher catches it and sends a
		// refresh call to the actuator ( wiremock in our test )
		Awaitilities.awaitUntilAsserted(120, 1000, () -> TestUtil.verifyActuatorCalled(1));

		// 6. Kubernetes assigned a new resource version to the updated ConfigMap.
		assertThat(secondResourceVersion).isNotEqualTo(firstResourceVersion);

		// 8. delete the current leader and wait until it is gone.
		String firstLeader = currentLeaderAccordingToLeaderLease(container);
		deletePod(container, firstLeader);
		Awaitilities.awaitUntilAsserted(120, 1000, () -> {
			// pods do not contain the leader anymore ( we have removed it )
			assertThat(runningPods(container)).doesNotContain(firstLeader);
			// but the lease still holds the pod that was removed ( since the lease has
			// not expired yet )
			assertThat(currentLeaderAccordingToLeaderLease(container)).isEqualTo(firstLeader);
		});
		// from the moment the above assertions pass, we have roughly 15 seconds
		// before a new pod acquires the leadership ( this is lease-duration )
		// within this time we need to patch configmap and make a few assertions
		// before a new leader is established

		// 9. update the ConfigMap while the old leader is gone and the lease has not
		// expired yet
		// also there is no leader to react to the patch in the configmap, so no actuator
		// call
		WireMock.resetAllRequests();
		patchConfigMap(container, "updated-after-leader-loss");
		String thirdResourceVersion = configMapResourceVersion(container);

		// resourceVersion has incremented in k8s
		assertThat(thirdResourceVersion).isNotEqualTo(secondResourceVersion);

		// no leader was active, so this update must not have triggered an actuator call
		WireMock.verify(WireMock.exactly(0), WireMock.postRequestedFor(WireMock.urlEqualTo("/actuator/refresh")));

		// 10. leadership is established again and the replacement leader observes the
		// current ConfigMap state and triggers a refresh
		Awaitilities.awaitUntilAsserted(120, 1000, () -> {
			String secondLeader = currentLeaderAccordingToLeaderLease(container);
			assertThat(secondLeader).isNotBlank().isNotEqualTo(firstLeader);
			assertThat(runningPods(container)).contains(secondLeader);
		});

		Awaitilities.awaitUntilAsserted(120, 1000, () -> TestUtil.verifyActuatorCalled(1));
	}

	private String currentLeaderAccordingToLeaderLease(K3sContainer container) {
		String exec = """
				kubectl get lease -n default spring-k8s-leader-election-lock \\
					-o "jsonpath={.spec.holderIdentity}"
				""";

		try {
			return container.execInContainer("sh", "-c", exec).getStdout().trim();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * get both pods as part of the replica of the deployment.
	 */
	private List<String> runningPods(K3sContainer container) {

		String exec = """
				kubectl get pods -n default -l app=spring-cloud-kubernetes-configuration-watcher \\
					--field-selector=status.phase=Running \\
					-o "jsonpath={.items[*].metadata.name}"
				""";

		try {
			String runningPods = container.execInContainer("sh", "-c", exec).getStdout().trim();

			return runningPods.isEmpty() ? List.of() : Arrays.stream(runningPods.split("\\s+")).toList();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * return the resourceVersion from the 'service-wiremock' configmap.
	 */
	private String configMapResourceVersion(K3sContainer container) {

		String exec = """
				kubectl get configmap service-wiremock -n default \\
					-o "jsonpath={.metadata.resourceVersion}"
				""";

		try {
			return container.execInContainer("sh", "-c", exec).getStdout().trim();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void patchConfigMap(K3sContainer container, String value) {
		String exec = """
				kubectl patch configmap service-wiremock -n default --type merge \\
					-p '{"data":{"foo":"%s"}}'
				""".formatted(value);

		try {
			container.execInContainer("sh", "-c", exec);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// Kill the pod without graceful shutdown.
	// This prevents the leader-election code from releasing the Lease.
	// The next replica must wait for the Lease to expire before acquiring leadership.
	// so we wait until it is removed, but do not --force it.
	private void deletePod(K3sContainer container, String podName) {
		try {
			String exec = """
					kubectl delete pod -n default ${podName} --grace-period=0 --wait=true
					""".replace("${podName}", podName);
			container.execInContainer("sh", "-c", exec);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
