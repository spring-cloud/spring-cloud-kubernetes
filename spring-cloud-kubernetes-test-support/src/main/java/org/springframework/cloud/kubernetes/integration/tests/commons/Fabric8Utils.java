/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.integration.tests.commons;

import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;

/**
 * @author wind57
 */
public final class Fabric8Utils {

	private static final Log LOG = LogFactory.getLog(Fabric8Utils.class);

	private Fabric8Utils() {
		throw new AssertionError("no instance provided");
	}

	public static FileInputStream inputStream(String fileName) throws Exception {
		ClassLoader classLoader = Fabric8Utils.class.getClassLoader();
		File file = new File(classLoader.getResource(fileName).getFile());
		return new FileInputStream(file);
	}

	public static void waitForDeployment(KubernetesClient client, String deploymentName, String namespace,
			int pollSeconds, int maxSeconds) {
		await().pollInterval(Duration.ofSeconds(pollSeconds)).atMost(maxSeconds, TimeUnit.SECONDS)
				.until(() -> isDeploymentReady(client, deploymentName, namespace));
	}

	public static void waitForEndpoint(KubernetesClient client, String endpointName, String namespace, int pollSeconds,
			int maxSeconds) {
		await().pollInterval(Duration.ofSeconds(pollSeconds)).atMost(maxSeconds, TimeUnit.SECONDS)
				.until(() -> isEndpointReady(client, endpointName, namespace));
	}

	private static boolean isDeploymentReady(KubernetesClient client, String deploymentName, String namespace) {

		Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();

		Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
		LOG.info("Available replicas for " + deploymentName + ": " + availableReplicas);
		return availableReplicas != null && availableReplicas >= 1;
	}

	private static boolean isEndpointReady(KubernetesClient client, String endpointName, String namespace) {

		Endpoints endpoint = client.endpoints().inNamespace(namespace).withName(endpointName).get();

		if (endpoint.getSubsets().isEmpty()) {
			fail("no endpoints for " + endpointName);
		}

		return endpoint.getSubsets().get(0).getAddresses().size() >= 1;
	}

	public static void setUp(KubernetesClient client, String namespace) throws Exception {
		FileInputStream serviceAccountAsStream = inputStream("setup/service-account.yaml");
		FileInputStream roleBindingAsStream = inputStream("setup/role-binding.yaml");
		FileInputStream roleAsStream = inputStream("setup/role.yaml");

		ServiceAccount serviceAccountFromStream = client.serviceAccounts().load(serviceAccountAsStream).get();
		if (client.serviceAccounts().inNamespace(namespace).withName(serviceAccountFromStream.getMetadata().getName())
				.get() == null) {
			client.serviceAccounts().inNamespace(namespace).create(serviceAccountFromStream);
		}

		RoleBinding roleBindingFromStream = client.rbac().roleBindings().load(roleBindingAsStream).get();
		if (client.rbac().roleBindings().inNamespace(namespace).withName(roleBindingFromStream.getMetadata().getName())
				.get() == null) {
			client.rbac().roleBindings().inNamespace(namespace).create(roleBindingFromStream);
		}

		Role roleFromStream = client.rbac().roles().load(roleAsStream).get();
		if (client.rbac().roles().inNamespace(namespace).withName(roleFromStream.getMetadata().getName())
				.get() == null) {
			client.rbac().roles().inNamespace(namespace).create(roleFromStream);
		}

	}

}
