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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Deployment;
import io.kubernetes.client.models.V1DeploymentBuilder;
import io.kubernetes.client.models.V1DeploymentList;
import io.kubernetes.client.models.V1Endpoints;
import io.kubernetes.client.models.V1EndpointsList;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ReplicationController;
import io.kubernetes.client.models.V1ReplicationControllerList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceBuilder;
import io.kubernetes.client.util.Yaml;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;

/**
 * @author Ryan Baxter
 */
public class K8SUtils {

	private Log log = LogFactory.getLog(getClass());

	private CoreV1Api api;

	private AppsV1Api appsApi;

	public K8SUtils(CoreV1Api api, AppsV1Api appsApi) {
		this.api = api;
		this.appsApi = appsApi;
	}

	public Object readYaml(String urlString) throws Exception {
		// create the url
		URL url = new URL(urlString);
		BufferedReader reader = null;
		Object yamlObj = null;
		try {
			// open the url stream, wrap it an a few "readers"
			reader = new BufferedReader(new InputStreamReader(url.openStream()));
			yamlObj = Yaml.load(reader);
		}
		catch (Exception e) {
			throw e;
		}
		finally {
			if (reader != null) {
				reader.close();
			}
		}
		return yamlObj;
	}

	public Object readYamlFromClasspath(String fileName) throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(fileName).getFile());
		BufferedReader reader = null;
		Object yamlObj = null;
		try {
			// open the url stream, wrap it an a few "readers"
			reader = new BufferedReader(new FileReader(file));
			yamlObj = Yaml.load(reader);
		}
		catch (Exception e) {
			throw e;
		}
		finally {
			if (reader != null) {
				reader.close();
			}
		}
		return yamlObj;
	}

	public V1Service createService(String name, Map<String, String> labels,
			Map<String, String> specSelectors, String type, String portName, int port,
			int targetPort, String namespace) throws ApiException {
		V1Service wiremockService = new V1ServiceBuilder().editOrNewMetadata()
				.withName(name).addToLabels(labels).endMetadata().editOrNewSpec()
				.addToSelector(specSelectors).withNewType(type).addNewPort()
				.withName(portName).withPort(port).withNewTargetPort(targetPort).endPort()
				.endSpec().build();
		return api.createNamespacedService(namespace, wiremockService, null, null, null);
	}

	public V1Deployment createDeployment(String name,
			Map<String, String> selectorMatchLabels,
			Map<String, String> templateMetadataLabels, String containerName,
			String image, String pullPolicy, int containerPort, int readinessProbePort,
			String readinessProbePath, int livenessProbePort, String livenessProbePath,
			String serviceAccountName, Collection<V1EnvVar> envVars, String namespace)
			throws ApiException {

		V1Deployment wiremockDeployment = new V1DeploymentBuilder().editOrNewMetadata()
				.withName(name).endMetadata().editOrNewSpec().withNewSelector()
				.addToMatchLabels(selectorMatchLabels).endSelector().editOrNewTemplate()
				.editOrNewMetadata().addToLabels(templateMetadataLabels).endMetadata()
				.editOrNewSpec().withServiceAccountName(serviceAccountName)
				.addNewContainer().withName(containerName).withImage(image)
				.withImagePullPolicy(pullPolicy).addNewPort()
				.withContainerPort(containerPort).endPort().editOrNewReadinessProbe()
				.editOrNewHttpGet().withNewPort(readinessProbePort)
				.withNewPath(readinessProbePath).endHttpGet().endReadinessProbe()
				.editOrNewLivenessProbe().editOrNewHttpGet()
				.withNewPort(livenessProbePort).withNewPath(livenessProbePath)
				.endHttpGet().endLivenessProbe().addAllToEnv(envVars).endContainer()
				.endSpec().endTemplate().endSpec().build();
		return appsApi.createNamespacedDeployment(namespace, wiremockDeployment, null,
				null, null);

	}

	public void waitForEndpointReady(String name, String namespace) throws Exception {
		await().pollInterval(Duration.ofSeconds(1)).atMost(90, TimeUnit.SECONDS)
				.until(() -> isEndpointReady(name, namespace));
	}

	public boolean isEndpointReady(String name, String namespace) throws ApiException {
		V1EndpointsList endpoints = api.listNamespacedEndpoints(namespace, null, null,
				"metadata.name=" + name, null, null, null, null, null);
		if (endpoints.getItems().isEmpty()) {
			fail("no endpoints for " + name);
		}
		V1Endpoints endpoint = endpoints.getItems().get(0);
		return endpoint.getSubsets().get(0).getAddresses().size() >= 1;
	}

	public void waitForReplicationController(String name, String namespace) {
		await().pollInterval(Duration.ofSeconds(1)).atMost(90, TimeUnit.SECONDS)
				.until(() -> isReplicationControllerReady(name, namespace));
	}

	public boolean isReplicationControllerReady(String name, String namespace)
			throws ApiException {
		V1ReplicationControllerList controllerList = api
				.listNamespacedReplicationController(namespace, null, null,
						"metadata.name=" + name, null, null, null, null, null);
		if (controllerList.getItems().size() < 1) {
			fail("Replication controller with name " + name + "could not be found");
		}

		V1ReplicationController replicationController = controllerList.getItems().get(0);
		Integer availableReplicas = replicationController.getStatus()
				.getAvailableReplicas();
		log.info("Available replicas for " + name + ": " + availableReplicas);
		return availableReplicas != null && availableReplicas >= 1;

	}

	public void waitForDeployment(String deploymentName, String namespace) {
		await().pollInterval(Duration.ofSeconds(1)).atMost(90, TimeUnit.SECONDS)
				.until(() -> isDeployentReady(deploymentName, namespace));
	}

	public boolean isDeployentReady(String deploymentName, String namespace)
			throws ApiException {
		V1DeploymentList deployments = appsApi.listNamespacedDeployment(namespace, null,
				null, "metadata.name=" + deploymentName, null, null, null, null, null);
		if (deployments.getItems().size() < 1) {
			fail("No deployments with the name " + deploymentName);
		}
		V1Deployment deployment = deployments.getItems().get(0);
		Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
		log.info("Available replicas for " + deploymentName + ": " + availableReplicas);
		return availableReplicas != null && availableReplicas >= 1;
	}

}
