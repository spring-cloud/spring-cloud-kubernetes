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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import io.kubernetes.client.openapi.models.V1EnvVar;
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
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.loadImage;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pullImage;

/**
 * @author wind57
 */
public final class NativeClientKubernetesFixture {

	private static final Log LOG = LogFactory.getLog(NativeClientKubernetesFixture.class);

	private final CoreV1Api coreV1Api;

	private final AppsV1Api appsV1Api;

	private final RbacAuthorizationV1Api rbacApi;

	private final K3sContainer container;

	public NativeClientKubernetesFixture(K3sContainer container) {

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

			coreV1Api.createNamespacedService(namespace, service).execute();

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

				appsV1Api.createNamespacedDeployment(namespace, deployment).execute();
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
		try {
			if (configMap != null) {
				coreV1Api.createNamespacedConfigMap(namespace, configMap).execute();
				waitForConfigMap(namespace, configMap, Phase.CREATE);
			}

			if (secret != null) {
				coreV1Api.createNamespacedSecret(namespace, secret).execute();
				waitForSecret(namespace, secret, Phase.CREATE);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void deleteAndWait(String namespace, @Nullable V1ConfigMap configMap, @Nullable V1Secret secret) {
		try {
			if (configMap != null) {
				String configMapName = configMapName(configMap);
				coreV1Api.deleteNamespacedConfigMap(configMapName, namespace).execute();
				waitForConfigMap(namespace, configMap, Phase.DELETE);
			}

			if (secret != null) {
				String secretName = secretName(secret);
				coreV1Api.deleteNamespacedSecret(secretName, namespace).execute();
				waitForSecret(namespace, secret, Phase.DELETE);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void createNamespace(String name) {

		if ("default".equals(name)) {
			return;
		}

		try {
			coreV1Api.createNamespace(new V1NamespaceBuilder().withNewMetadata().withName(name).and().build())
				.execute();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void deleteAndWait(String namespace, V1Deployment deployment, V1Service service) {

		try {
			if (deployment != null) {
				String deploymentName = deploymentName(deployment);

				boolean deploymentPresent = true;
				V1Deployment k8sDeployment = null;
				try {
					k8sDeployment = appsV1Api.readNamespacedDeployment(deploymentName, namespace).execute();
				}
				catch (ApiException apiException) {
					if (apiException.getCode() == 404) {
						deploymentPresent = false;
					}
					else {
						throw new RuntimeException(apiException);
					}
				}

				if (deploymentPresent) {
					Map<String, String> podLabels = k8sDeployment.getSpec().getTemplate().getMetadata().getLabels();
					appsV1Api.deleteNamespacedDeployment(deploymentName, namespace).execute();
					coreV1Api.deleteCollectionNamespacedPod(namespace)
						.labelSelector(labelSelector(podLabels))
						.execute();
					waitForDeploymentToBeDeleted(deploymentName, namespace);
					waitForDeploymentPodsToBeDeleted(podLabels, namespace);
				}

			}

			if (service != null) {

				service.getMetadata().setNamespace(namespace);
				boolean servicePresent = true;

				try {
					coreV1Api
						.readNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace())
						.execute();
				}
				catch (ApiException apiException) {
					if (apiException.getCode() == 404) {
						servicePresent = false;
					}
					else {
						throw new RuntimeException(apiException);
					}
				}

				if (servicePresent) {
					coreV1Api
						.deleteNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace())
						.execute();
					waitForServiceToBeDeleted(service.getMetadata().getName(), namespace);
				}
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void busybox(String namespace, Phase phase) {
		V1Deployment deployment = yaml("busybox/deployment.yaml", V1Deployment.class);

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.busyboxVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = yaml("busybox/service.yaml", V1Service.class);
		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "busybox", deployment, service, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service);
		}
	}

	public void kafka(Phase phase) {
		V1Deployment deployment = yaml("kafka/kafka-deployment.yaml", V1Deployment.class);

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.kafkaVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = yaml("kafka/kafka-service.yaml", V1Service.class);

		if (phase.equals(Phase.CREATE)) {
			createAndWait("default", "kafka", deployment, service, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait("default", deployment, service);
		}
	}

	public void rabbitMq(Phase phase) {
		V1Deployment deployment = yaml("rabbitmq/rabbitmq-deployment.yaml", V1Deployment.class);

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.rabbitMqVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = yaml("rabbitmq/rabbitmq-service.yaml", V1Service.class);

		if (phase.equals(Phase.CREATE)) {
			createAndWait("default", "rabbitmq", deployment, service, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait("default", deployment, service);
		}
	}

	/**
	 * reads a yaml from classpath, fails if not found.
	 */
	public <T> T yaml(String fileName, Class<T> type) {
		ClassLoader classLoader = NativeClientKubernetesFixture.class.getClassLoader();

		try (InputStream inputStream = classLoader.getResourceAsStream(fileName)) {
			if (inputStream == null) {
				throw new IllegalArgumentException("Resource not found: " + fileName);
			}

			String file = new BufferedReader(new InputStreamReader(inputStream)).lines()
				.collect(Collectors.joining("\n"));

			return Yaml.loadAs(file, type);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to load yaml resource: " + fileName, e);
		}
	}

	public void setUp(String namespace) {

		try {
			V1ServiceAccount serviceAccount = yaml("setup/service-account.yaml", V1ServiceAccount.class);
			CheckedSupplier<V1ServiceAccount> accountSupplier = () -> coreV1Api
				.readNamespacedServiceAccount(serviceAccount.getMetadata().getName(), namespace)
				.execute();
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
				.createNamespacedServiceAccount(namespace, serviceAccount)
				.execute();
			notExistsHandler(accountSupplier, accountDefaulter);

			V1RoleBinding roleBinding = yaml("setup/role-binding.yaml", V1RoleBinding.class);
			notExistsHandler(
					() -> rbacApi.readNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace).execute(),
					() -> rbacApi.createNamespacedRoleBinding(namespace, roleBinding).execute());

			V1Role role = yaml("setup/role.yaml", V1Role.class);
			notExistsHandler(() -> rbacApi.readNamespacedRole(role.getMetadata().getName(), namespace).execute(),
					() -> rbacApi.createNamespacedRole(namespace, role).execute());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void setUpClusterWide(String serviceAccountNamespace, String[] roleBindingNamespaces) {

		try {
			V1ServiceAccount serviceAccount = yaml("cluster/service-account.yaml", V1ServiceAccount.class);
			CheckedSupplier<V1ServiceAccount> accountSupplier = () -> coreV1Api
				.readNamespacedServiceAccount(serviceAccount.getMetadata().getName(), serviceAccountNamespace)
				.execute();
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
				.createNamespacedServiceAccount(serviceAccountNamespace, serviceAccount)
				.execute();
			notExistsHandler(accountSupplier, accountDefaulter);

			V1ClusterRole clusterRole = yaml("cluster/cluster-role.yaml", V1ClusterRole.class);
			notExistsHandler(() -> rbacApi.readClusterRole(clusterRole.getMetadata().getName()).execute(),
					() -> rbacApi.createClusterRole(clusterRole).execute());

			V1RoleBinding roleBinding = yaml("cluster/role-binding.yaml", V1RoleBinding.class);
			for (String roleBindingNamespace : roleBindingNamespaces) {
				roleBinding.getMetadata().setNamespace(roleBindingNamespace);
				try {
					notExistsHandler(
							() -> rbacApi
								.readNamespacedRoleBinding(roleBinding.getMetadata().getName(), roleBindingNamespace)
								.execute(),
							() -> rbacApi.createNamespacedRoleBinding(roleBindingNamespace, roleBinding).execute());
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void deleteClusterWide(String serviceAccountNamespace, String[] roleBindingNamespaces) {
		try {
			V1ServiceAccount serviceAccount = yaml("cluster/service-account.yaml", V1ServiceAccount.class);
			V1ClusterRole clusterRole = yaml("cluster/cluster-role.yaml", V1ClusterRole.class);
			V1RoleBinding roleBinding = yaml("cluster/role-binding.yaml", V1RoleBinding.class);

			coreV1Api.deleteNamespacedServiceAccount(serviceAccount.getMetadata().getName(), serviceAccountNamespace)
				.execute();
			rbacApi.deleteClusterRole(clusterRole.getMetadata().getName()).execute();
			for (String roleBindingNamespace : roleBindingNamespaces) {
				roleBinding.getMetadata().setNamespace(roleBindingNamespace);
				try {
					rbacApi.deleteNamespacedRoleBinding(roleBinding.getMetadata().getName(), roleBindingNamespace)
						.execute();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteNamespace(String name) {

		if ("default".equals(name)) {
			return;
		}

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
					try {
						apiInstance.deleteAPIService(apiServiceName).execute();
					}
					catch (ApiException e) {
						throw new RuntimeException(e);
					}
				});

			coreV1Api.deleteNamespace(name).execute();
		}
		catch (ApiException e) {
			System.out.println(e.getResponseBody());
			throw new RuntimeException(e);
		}

		Awaitilities.awaitUntil(30, 1000, () -> {
			try {
				return coreV1Api.listNamespace()
					.execute()
					.getItems()
					.stream()
					.noneMatch(x -> x.getMetadata().getName().equals(name));
			}
			catch (ApiException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public void wiremock(String namespace, Phase phase, boolean withNodePort) {
		V1Deployment deployment = yaml("wiremock/wiremock-deployment.yaml", V1Deployment.class);

		String imageWithoutVersion = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		String imageWithVersion = imageWithoutVersion + ":" + Images.wiremockVersion();
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageWithVersion);

		V1Service service = yaml("wiremock/wiremock-service.yaml", V1Service.class);
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

	public void externalName(Phase phase) {
		V1Service service = yaml("external-name-service/external-name-service.yaml", V1Service.class);
		if (Phase.CREATE.equals(phase)) {
			createAndWait("default", null, null, service, false);
		}
		else {
			deleteAndWait("default", null, service);
		}
	}

	public void configWatcher(Phase phase, String refreshDelay, boolean reloadEnabled, String[] watchNamespaces,
			boolean kafkaEnabled, boolean rabbitMqEnabled) {

		V1Deployment deployment = yaml("config-watcher/deployment.yaml", V1Deployment.class);
		V1Service service = yaml("config-watcher/service.yaml", V1Service.class);

		List<V1EnvVar> envVars = new ArrayList<>();
		envVars
			.add(new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER_REFRESHDELAY").value(refreshDelay));
		envVars.add(new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_ENABLED").value(String.valueOf(reloadEnabled)));
		envVars.add(new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CONFIGURATION_WATCHER")
			.value("DEBUG"));
		envVars.add(new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD")
			.value("DEBUG"));
		envVars.add(new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_SECRETS_ENABLED").value("TRUE"));

		if (kafkaEnabled) {
			envVars.add(new V1EnvVar().name("SPRING_PROFILES_ACTIVE").value("bus-kafka"));
			envVars.add(new V1EnvVar().name("SPRING_CLOUD_BUS_DESTINATION").value("app"));
			envVars.add(new V1EnvVar().name("spring.kafka.bootstrap-servers").value("kafka:9092"));
			envVars.add(new V1EnvVar().name("LOGGING_LEVEL_ORG_SPRING_CLOUD_KUBERNETES_CONFIGURATION_WATCHER")
				.value("DEBUG"));
		}

		if (rabbitMqEnabled) {
			envVars.add(new V1EnvVar().name("SPRING_PROFILES_ACTIVE").value("bus-amqp"));
			envVars.add(new V1EnvVar().name("SPRING_RABBITMQ_HOST").value("rabbitmq-service"));
			envVars.add(new V1EnvVar().name("SPRING_CLOUD_BUS_DESTINATION").value("app"));
		}

		// SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_0 = a
		// SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_1 = b
		if (watchNamespaces != null && watchNamespaces.length > 0) {
			for (int i = 0; i < watchNamespaces.length; i++) {
				envVars.add(new V1EnvVar().name("SPRING_CLOUD_KUBERNETES_RELOAD_NAMESPACES_" + i)
					.value(watchNamespaces[i]));
			}
		}

		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

		if (phase.equals(Phase.CREATE)) {
			createAndWait("default", deployment.getMetadata().getName(), deployment, service, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait("default", deployment, service);
		}
	}

	public void discoveryServer(Phase phase) {
		V1Deployment deployment = yaml("discovery-server/discoveryserver-deployment.yaml", V1Deployment.class);
		V1Service service = yaml("discovery-server/discoveryserver-service.yaml", V1Service.class);

		if (phase == Phase.CREATE) {
			createAndWait("default", null, deployment, service, true);
		}
		else {
			deleteAndWait("default", null, service);
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
		Awaitilities.awaitUntil(6000, 1000, () -> {
			try {
				return isDeploymentReady(deploymentName, namespace);
			}
			catch (ApiException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private void waitForConfigMap(String namespace, V1ConfigMap configMap, Phase phase) {
		String configMapName = configMapName(configMap);

		Awaitilities.awaitUntil(600, 1000, () -> {
			if (phase == Phase.DELETE) {
				try {
					coreV1Api.readNamespacedConfigMap(configMapName, namespace).execute();
				}
				catch (ApiException apiException) {
					if (apiException.getCode() == 404) {
						// a 404 here means it was deleted
						return true;
					}
					throw new RuntimeException(apiException);
				}
				// if we did not get an ApiException, we still have the resource,
				// retry as such, because we want it deleted
				return false;
			}
			else {
				try {
					coreV1Api.readNamespacedConfigMap(configMapName, namespace).execute();
				}
				catch (ApiException apiException) {
					if (apiException.getCode() == 404) {
						// we want it created, but it's not yet
						return false;
					}
					throw new RuntimeException(apiException);
				}
				// if we did not get an ApiException, we have the resource created
				return true;
			}
		});

	}

	private void waitForSecret(String namespace, V1Secret secret, Phase phase) {
		String secretName = secretName(secret);

		Awaitilities.awaitUntil(600, 1000, () -> {
			if (phase == Phase.DELETE) {
				try {
					coreV1Api.readNamespacedSecret(secretName, namespace).execute();
				}
				catch (ApiException apiException) {
					if (apiException.getCode() == 404) {
						// a 404 here means it was deleted
						return true;
					}
					throw new RuntimeException(apiException);
				}
				// if we did not get an ApiException, we still have the resource,
				// retry as such, because we want it deleted
				return false;
			}
			else {
				try {
					coreV1Api.readNamespacedSecret(secretName, namespace).execute();
				}
				catch (ApiException apiException) {
					if (apiException.getCode() == 404) {
						// we want it created, but it's not yet
						return false;
					}
					throw new RuntimeException(apiException);
				}
				// if we did not get an ApiException, we have the resource created
				return true;
			}
		});

	}

	private void waitForDeploymentToBeDeleted(String deploymentName, String namespace) {

		Awaitilities.awaitUntil(180, 1000, () -> {
			try {
				appsV1Api.readNamespacedDeployment(deploymentName, namespace).execute();
				return false;
			}
			catch (ApiException e) {
				if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					return true;
				}
				throw new RuntimeException(e);
			}
		});

	}

	private void waitForServiceToBeDeleted(String serviceName, String namespace) {

		Awaitilities.awaitUntil(180, 1000, () -> {
			try {
				coreV1Api.readNamespacedService(serviceName, namespace).execute();
				return false;
			}
			catch (ApiException e) {
				if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					return true;
				}
				throw new RuntimeException(e);
			}
		});

	}

	private void waitForDeploymentPodsToBeDeleted(Map<String, String> labels, String namespace) {

		Awaitilities.awaitUntil(180, 1000, () -> {
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
			logDeploymentConditions(deployment.getStatus().getConditions(), deployment.getMetadata().getNamespace());
			LOG.info("Available replicas for " + deploymentName + ": "
					+ (availableReplicas == null ? 0 : availableReplicas));
			return availableReplicas != null && availableReplicas >= 1;
		}
		else {
			return false;
		}
	}

	private void logDeploymentConditions(List<V1DeploymentCondition> conditions, String namespace) {
		if (conditions != null) {
			for (V1DeploymentCondition condition : conditions) {
				LOG.info("Deployment Condition Type: " + condition.getType());
				LOG.info("Deployment Condition Status: " + condition.getStatus());
				LOG.info("Deployment Condition Message: " + condition.getMessage());
				LOG.info("Deployment Condition Reason: " + condition.getReason());

				if (condition.getReason() != null && condition.getReason().contains("ProgressDeadlineExceeded")) {
					try {
						Container.ExecResult result = container.execInContainer("sh", "-c",
								"kubectl get events -n " + namespace);
						LOG.info("events from namespace : " + result.getStdout());
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

	private <T> void notExistsHandler(CheckedSupplier<T> callee, CheckedSupplier<T> defaulter) throws Exception {
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

	private String labelSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(en -> en.getKey() + "=" + en.getValue()).collect(Collectors.joining(","));
	}

	private interface CheckedSupplier<T> {

		T get() throws Exception;

	}

}
