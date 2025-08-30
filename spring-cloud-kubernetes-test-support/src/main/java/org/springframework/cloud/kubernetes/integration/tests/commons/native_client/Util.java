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

package org.springframework.cloud.kubernetes.integration.tests.commons.native_client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.ApiregistrationV1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1APIService;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;
import jakarta.annotation.Nullable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.assertj.core.api.Assertions;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.loadImage;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pullImage;

/**
 * @author wind57
 */
public final class Util {

	private static final Log LOG = LogFactory.getLog(Util.class);

	private final CoreV1Api coreV1Api;

	private final AppsV1Api appsV1Api;

	private final RbacAuthorizationV1Api rbacApi;

	private final K3sContainer container;

	public Util(K3sContainer container) {

		ApiClient client;
		try {
			client = Config.fromConfig(new StringReader(container.getKubeConfigYaml()));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		client.setHttpClient(client.getHttpClient().newBuilder().readTimeout(Duration.ofSeconds(15)).build());
		client.setDebugging(false);
		Configuration.setDefaultApiClient(client);

		this.container = container;
		this.coreV1Api = new CoreV1Api();
		this.appsV1Api = new AppsV1Api();
		rbacApi = new RbacAuthorizationV1Api();
	}

	/**
	 * This is the preferred method to use when creating a deployment alongside with a
	 * service. It creates the given resources as-well as waits for them to be created.
	 * The delay check is intentionally not taken as an argument, so that it stays as
	 * tight as possible, providing reasonable defaults.
	 *
	 */
	public void createAndWait(String namespace, String name, V1Deployment deployment, V1Service service,
			boolean changeVersion) {
		try {

			coreV1Api.createNamespacedService(namespace, service);

			if (deployment != null) {
				String imageFromDeployment = deployment.getSpec()
					.getTemplate()
					.getSpec()
					.getContainers()
					.get(0)
					.getImage();
				if (changeVersion) {
					deployment.getSpec()
						.getTemplate()
						.getSpec()
						.getContainers()
						.get(0)
						.setImage(imageFromDeployment + ":" + pomVersion());
				}
				else {
					String[] image = imageFromDeployment.split(":", 2);
					pullImage(image[0], image[1], name, container);
					loadImage(image[0], image[1], name, container);
				}

				appsV1Api.createNamespacedDeployment(namespace, deployment);
				waitForDeployment(namespace, deployment);
			}

		}
		catch (Exception e) {
			if (e instanceof ApiException apiException) {
				System.out.println(apiException.getResponseBody());
			}
			throw new RuntimeException(e);
		}
	}

	public void createAndWait(String namespace, @Nullable V1ConfigMap configMap, @Nullable V1Secret secret) {
		if (configMap != null) {
			coreV1Api.createNamespacedConfigMap(namespace, configMap);
			waitForConfigMap(namespace, configMap, Phase.CREATE);
		}

		if (secret != null) {
			coreV1Api.createNamespacedSecret(namespace, secret);
			waitForSecret(namespace, secret, Phase.CREATE);
		}

	}

	public void deleteAndWait(String namespace, @Nullable V1ConfigMap configMap, @Nullable V1Secret secret) {
		if (configMap != null) {
			String configMapName = configMapName(configMap);
			coreV1Api.deleteNamespacedConfigMap(configMapName, namespace);
			waitForConfigMap(namespace, configMap, Phase.DELETE);
		}

		if (secret != null) {
			String secretName = secretName(secret);
			coreV1Api.deleteNamespacedSecret(secretName, namespace);
			waitForSecret(namespace, secret, Phase.DELETE);
		}

	}

	public void createNamespace(String name) {
		coreV1Api.createNamespace(new V1NamespaceBuilder().withNewMetadata().withName(name).and().build());
	}

	public void deleteAndWait(String namespace, V1Deployment deployment, V1Service service) {

		try {
			if (deployment != null) {
				String deploymentName = deploymentName(deployment);
				Map<String, String> podLabels = appsV1Api.readNamespacedDeployment(deploymentName, namespace)
					.execute()
					.getSpec()
					.getTemplate()
					.getMetadata()
					.getLabels();
				appsV1Api.deleteNamespacedDeployment(deploymentName, namespace);
				coreV1Api.deleteCollectionNamespacedPod(namespace).labelSelector(labelSelector(podLabels));
				waitForDeploymentToBeDeleted(deploymentName, namespace);
				waitForDeploymentPodsToBeDeleted(podLabels, namespace);
			}

			if (service != null) {
				service.getMetadata().setNamespace(namespace);
				coreV1Api.deleteNamespacedService(service.getMetadata().getName(),
						service.getMetadata().getNamespace());
				waitForServiceToBeDeleted(service.getMetadata().getName(), namespace);
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void busybox(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("busybox/deployment.yaml");

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.busyboxVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = (V1Service) yaml("busybox/service.yaml");
		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "busybox", deployment, service, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service);
		}
	}

	public void kafka(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("kafka/kafka-deployment.yaml");

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.kafkaVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = (V1Service) yaml("kafka/kafka-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "kafka", deployment, service, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service);
		}
	}

	public void rabbitMq(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("rabbitmq/rabbitmq-deployment.yaml");

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.rabbitMqVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = (V1Service) yaml("rabbitmq/rabbitmq-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "rabbitmq", deployment, service, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service);
		}
	}

	/**
	 * reads a yaml from classpath, fails if not found.
	 */
	public Object yaml(String fileName) {
		ClassLoader classLoader = Util.class.getClassLoader();
		String file = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(fileName))).lines()
			.collect(Collectors.joining("\n"));
		try {
			return Yaml.load(file);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void setUp(String namespace) {

		try {
			V1ServiceAccount serviceAccount = (V1ServiceAccount) yaml("setup/service-account.yaml");
			CheckedSupplier<V1ServiceAccount> accountSupplier = () -> coreV1Api
				.readNamespacedServiceAccount(serviceAccount.getMetadata().getName(), namespace)
				.execute();
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
				.createNamespacedServiceAccount(namespace, serviceAccount)
				.execute();
			notExistsHandler(accountSupplier, accountDefaulter);

			V1RoleBinding roleBinding = (V1RoleBinding) yaml("setup/role-binding.yaml");
			notExistsHandler(() -> rbacApi.readNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace),
					() -> rbacApi.createNamespacedRoleBinding(namespace, roleBinding));

			V1Role role = (V1Role) yaml("setup/role.yaml");
			notExistsHandler(() -> rbacApi.readNamespacedRole(role.getMetadata().getName(), namespace),
					() -> rbacApi.createNamespacedRole(namespace, role));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void setUpClusterWide(String serviceAccountNamespace, Set<String> namespaces) {

		try {
			V1ServiceAccount serviceAccount = (V1ServiceAccount) yaml("cluster/service-account.yaml");
			CheckedSupplier<V1ServiceAccount> accountSupplier = () -> coreV1Api
				.readNamespacedServiceAccount(serviceAccount.getMetadata().getName(), serviceAccountNamespace)
				.execute();
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
				.createNamespacedServiceAccount(serviceAccountNamespace, serviceAccount)
				.execute();
			notExistsHandler(accountSupplier, accountDefaulter);

			V1ClusterRole clusterRole = (V1ClusterRole) yaml("cluster/cluster-role.yaml");
			notExistsHandler(() -> rbacApi.readClusterRole(clusterRole.getMetadata().getName()),
					() -> rbacApi.createClusterRole(clusterRole));

			V1RoleBinding roleBinding = (V1RoleBinding) yaml("cluster/role-binding.yaml");
			namespaces.forEach(namespace -> {
				roleBinding.getMetadata().setNamespace(namespace);
				try {
					notExistsHandler(
							() -> rbacApi.readNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace),
							() -> rbacApi.createNamespacedRoleBinding(namespace, roleBinding));
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void deleteClusterWide(String serviceAccountNamespace, Set<String> namespaces) {
		try {
			V1ServiceAccount serviceAccount = (V1ServiceAccount) yaml("cluster/service-account.yaml");
			V1ClusterRole clusterRole = (V1ClusterRole) yaml("cluster/cluster-role.yaml");
			V1RoleBinding roleBinding = (V1RoleBinding) yaml("cluster/role-binding.yaml");

			coreV1Api.deleteNamespacedServiceAccount(serviceAccount.getMetadata().getName(), serviceAccountNamespace);
			rbacApi.deleteClusterRole(clusterRole.getMetadata().getName());
			namespaces.forEach(namespace -> {
				roleBinding.getMetadata().setNamespace(namespace);
				try {
					rbacApi.deleteNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteNamespace(String name) {

		// sometimes we get errors like :

		// "message": "Discovery failed for some groups,
		// 1 failing: unable to retrieve the complete list of server APIs:
		// metrics.k8s.io/v1beta1: stale GroupVersion discovery: metrics.k8s.io/v1beta1"

		// but even when it works OK, the finalizers are slowing down the deletion
		ApiregistrationV1Api apiInstance = new ApiregistrationV1Api(coreV1Api.getApiClient());
		List<V1APIService> apiServices;
		try {
			apiServices = apiInstance.listAPIService().execute().getItems();

			apiServices.stream()
				.map(apiService -> apiService.getMetadata().getName())
				.filter(apiServiceName -> apiServiceName.contains("metrics.k8s.io"))
				.findFirst()
				.ifPresent(apiServiceName -> {
					apiInstance.deleteAPIService(apiServiceName);
				});

			coreV1Api.deleteNamespace(name);
		}
		catch (ApiException e) {
			System.out.println(e.getResponseBody());
			throw new RuntimeException(e);
		}

		await().pollInterval(Duration.ofSeconds(1))
			.atMost(30, TimeUnit.SECONDS)
			.until(() -> coreV1Api.listNamespace()
				.execute()
				.getItems()
				.stream()
				.noneMatch(x -> x.getMetadata().getName().equals(name)));
	}

	public void wiremock(String namespace, Phase phase, boolean withNodePort) {
		V1Deployment deployment = (V1Deployment) yaml("wiremock/wiremock-deployment.yaml");

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.wiremockVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = (V1Service) yaml("wiremock/wiremock-service.yaml");
		service.getMetadata().setNamespace(namespace);
		if (!withNodePort) {
			// we assume we only have one 'http' port
			service.getSpec().getPorts().get(0).setNodePort(null);
			service.getSpec().setType("ClusterIP");
		}

		if (phase.equals(Phase.CREATE)) {
			deployment.getMetadata().setNamespace(namespace);
			service.getMetadata().setNamespace(namespace);
			createAndWait(namespace, "wiremock", deployment, service, false);
		}
		else {
			deleteAndWait(namespace, deployment, service);
		}

	}

	private String deploymentName(V1Deployment deployment) {
		return deployment.getMetadata().getName();
	}

	private String configMapName(V1ConfigMap configMap) {
		return configMap.getMetadata().getName();
	}

	private String secretName(V1Secret secret) {
		return secret.getMetadata().getName();
	}

	private void waitForDeployment(String namespace, V1Deployment deployment) {
		String deploymentName = deploymentName(deployment);
		await().pollDelay(Duration.ofSeconds(5))
			.pollInterval(Duration.ofSeconds(5))
			.atMost(900, TimeUnit.SECONDS)
			.until(() -> isDeploymentReady(deploymentName, namespace));
	}

	private void waitForConfigMap(String namespace, V1ConfigMap configMap, Phase phase) {
		String configMapName = configMapName(configMap);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).until(() -> {
			coreV1Api.readNamespacedConfigMap(configMapName, namespace);
			return phase.equals(Phase.CREATE);
		});
	}

	private void waitForSecret(String namespace, V1Secret secret, Phase phase) {
		String secretName = secretName(secret);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).until(() -> {
			coreV1Api.readNamespacedSecret(secretName, namespace);
			return phase.equals(Phase.CREATE);
		});
	}

	private void waitForDeploymentToBeDeleted(String deploymentName, String namespace) {
		await().timeout(Duration.ofSeconds(180)).until(() -> {
			appsV1Api.readNamespacedDeployment(deploymentName, namespace);
			return false;
		});
	}

	private void waitForServiceToBeDeleted(String serviceName, String namespace) {
		await().timeout(Duration.ofSeconds(180)).until(() -> {
			coreV1Api.readNamespacedService(serviceName, namespace);
			return false;
		});
	}

	private void waitForDeploymentPodsToBeDeleted(Map<String, String> labels, String namespace) {
		await().timeout(Duration.ofSeconds(180)).until(() -> {
			try {
				int currentNumberOfPods = coreV1Api.listNamespacedPod(namespace)
					.labelSelector(labelSelector(labels))
					.execute()
					.getItems()
					.size();
				return currentNumberOfPods == 0;
			}
			catch (ApiException e) {
				if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					return true;
				}
				throw new RuntimeException(e);
			}
		});
	}

	private boolean isDeploymentReady(String deploymentName, String namespace) throws ApiException {
		V1DeploymentList deployments = appsV1Api.listNamespacedDeployment(namespace)
			.fieldSelector("metadata.name=" + deploymentName)
			.execute();
		if (deployments.getItems().isEmpty()) {
			Assertions.fail("No deployments with the name " + deploymentName);
		}
		V1Deployment deployment = deployments.getItems().get(0);
		if (deployment.getStatus() != null) {
			Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
			logDeploymentConditions(deployment.getStatus().getConditions());
			LOG.info("Available replicas for " + deploymentName + ": "
					+ (availableReplicas == null ? 0 : availableReplicas));
			return availableReplicas != null && availableReplicas >= 1;
		}
		else {
			return false;
		}
	}

	private void logDeploymentConditions(List<V1DeploymentCondition> conditions) {
		if (conditions != null) {
			for (V1DeploymentCondition condition : conditions) {
				LOG.info("Deployment Condition Type: " + condition.getType());
				LOG.info("Deployment Condition Status: " + condition.getStatus());
				LOG.info("Deployment Condition Message: " + condition.getMessage());
				LOG.info("Deployment Condition Reason: " + condition.getReason());
			}
		}
	}

	private static boolean isDeploymentReadyAfterPatch(String deploymentName, String namespace,
			Map<String, String> podLabels) throws ApiException {

		V1DeploymentList deployments = new AppsV1Api().listNamespacedDeployment(namespace)
			.fieldSelector("metadata.name=" + deploymentName)
			.execute();
		if (deployments.getItems().isEmpty()) {
			Assertions.fail("No deployment with name " + deploymentName);
		}

		V1Deployment deployment = deployments.getItems().get(0);
		// if no replicas are defined, it means only 1 is needed
		int replicas = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(1);
		int readyReplicas = Optional.ofNullable(deployment.getStatus().getReadyReplicas()).orElse(0);

		if (readyReplicas != replicas) {
			LOG.info("ready replicas not yet same as replicas");
			return false;
		}

		int pods = new CoreV1Api().listNamespacedPod(namespace)
			.labelSelector(labelSelector(podLabels))
			.execute()
			.getItems()
			.size();

		if (pods != replicas) {
			LOG.info("number of pods not yet stabilized");
			return false;
		}

		return true;
	}

	private static <T> void notExistsHandler(CheckedSupplier<T> callee, CheckedSupplier<T> defaulter) throws Exception {
		try {
			callee.get();
		}
		catch (Exception exception) {
			if (exception instanceof ApiException apiException) {
				if (apiException.getCode() == 404) {
					defaulter.get();
					return;
				}
			}
			throw new RuntimeException(exception);
		}
	}

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(en -> en.getKey() + "=" + en.getValue()).collect(Collectors.joining(","));
	}

	private interface CheckedSupplier<T> {

		T get() throws Exception;

	}

}
