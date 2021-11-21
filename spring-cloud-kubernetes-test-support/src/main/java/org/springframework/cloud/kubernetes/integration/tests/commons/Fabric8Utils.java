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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.awaitility.Awaitility.await;

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

	private static boolean isDeploymentReady(KubernetesClient client, String deploymentName, String namespace) {

		Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();

		Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
		LOG.info("Available replicas for " + deploymentName + ": " + availableReplicas);
		return availableReplicas != null && availableReplicas >= 1;
	}

}
