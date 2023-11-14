/*
 * Copyright 2013-2022 the original author or authors.
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

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressLoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1IngressLoadBalancerStatus;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.Yaml;
import jakarta.annotation.Nullable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
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

	private final NetworkingV1Api networkingV1Api;

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
		this.networkingV1Api = new NetworkingV1Api();
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
			@Nullable V1Ingress ingress, boolean changeVersion) {
		try {

			coreV1Api.createNamespacedService(namespace, service, null, null, null, null);

			if (deployment != null) {
				String imageFromDeployment = deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
						.getImage();
				if (changeVersion) {
					deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
							.setImage(imageFromDeployment + ":" + pomVersion());
				}
				else {
					String[] image = imageFromDeployment.split(":", 2);
					pullImage(image[0], image[1], container);
					loadImage(image[0], image[1], name, container);
				}

				appsV1Api.createNamespacedDeployment(namespace, deployment, null, null, null, null);
				waitForDeployment(namespace, deployment);
			}

			if (ingress != null) {
				networkingV1Api.createNamespacedIngress(namespace, ingress, null, null, null, null);
				waitForIngress(namespace, ingress);
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
				coreV1Api.createNamespacedConfigMap(namespace, configMap, null, null, null, null);
				waitForConfigMap(namespace, configMap, Phase.CREATE);
			}

			if (secret != null) {
				coreV1Api.createNamespacedSecret(namespace, secret, null, null, null, null);
				waitForSecret(namespace, secret, Phase.CREATE);
			}

		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteAndWait(String namespace, @Nullable V1ConfigMap configMap, @Nullable V1Secret secret) {
		try {
			if (configMap != null) {
				String configMapName = configMapName(configMap);
				coreV1Api.deleteNamespacedConfigMap(configMapName, namespace, null, null, null, null, null, null);
				waitForConfigMap(namespace, configMap, Phase.DELETE);
			}

			if (secret != null) {
				String secretName = secretName(secret);
				coreV1Api.deleteNamespacedSecret(secretName, namespace, null, null, null, null, null, null);
				waitForSecret(namespace, secret, Phase.DELETE);
			}

		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	public void createNamespace(String name) {
		try {
			coreV1Api.createNamespace(new V1NamespaceBuilder().withNewMetadata().withName(name).and().build(), null,
					null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteAndWait(String namespace, V1Deployment deployment, V1Service service,
			@Nullable V1Ingress ingress) {

		if (deployment != null) {
			try {
				String deploymentName = deploymentName(deployment);
				Map<String, String> podLabels = appsV1Api.readNamespacedDeployment(deploymentName, namespace, null)
						.getSpec().getTemplate().getMetadata().getLabels();
				appsV1Api.deleteNamespacedDeployment(deploymentName, namespace, null, null, null, null, null, null);
				coreV1Api.deleteCollectionNamespacedPod(namespace, null, null, null, null, null,
						labelSelector(podLabels), null, null, null, null, null, null, null, null);
				waitForDeploymentToBeDeleted(deploymentName, namespace);
				waitForDeploymentPodsToBeDeleted(podLabels, namespace);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}

		}

		String serviceName = serviceName(service);
		try {
			coreV1Api.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null);
			if (ingress != null) {
				String ingressName = ingressName(ingress);
				networkingV1Api.deleteNamespacedIngress(ingressName, namespace, null, null, null, null, null, null);
				waitForIngressToBeDeleted(ingressName, namespace);
			}

		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

	public void busybox(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("busybox/deployment.yaml");
		V1Service service = (V1Service) yaml("busybox/service.yaml");
		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "busybox", deployment, service, null, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service, null);
		}
	}

	public void kafka(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("kafka/kafka-deployment.yaml");
		V1Service service = (V1Service) yaml("kafka/kafka-service.yaml");
		V1ConfigMap configMap = (V1ConfigMap) yaml("kafka/kafka-configmap-startup-script.yaml");

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, configMap, null);
			createAndWait(namespace, "kafka", deployment, service, null, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, configMap, null);
			deleteAndWait(namespace, deployment, service, null);
		}
	}

	public void rabbitMq(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("rabbitmq/rabbitmq-deployment.yaml");
		V1Service service = (V1Service) yaml("rabbitmq/rabbitmq-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "rabbitmq", deployment, service, null, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service, null);
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
					.readNamespacedServiceAccount(serviceAccount.getMetadata().getName(), namespace, null);
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
					.createNamespacedServiceAccount(namespace, serviceAccount, null, null, null, null);
			notExistsHandler(accountSupplier, accountDefaulter);

			V1RoleBinding roleBinding = (V1RoleBinding) yaml("setup/role-binding.yaml");
			notExistsHandler(
					() -> rbacApi.readNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace, null),
					() -> rbacApi.createNamespacedRoleBinding(namespace, roleBinding, null, null, null, null));

			V1Role role = (V1Role) yaml("setup/role.yaml");
			notExistsHandler(() -> rbacApi.readNamespacedRole(role.getMetadata().getName(), namespace, null),
					() -> rbacApi.createNamespacedRole(namespace, role, null, null, null, null));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void setUpClusterWideClusterRoleBinding(String serviceAccountNamespace) {

		try {
			V1ServiceAccount serviceAccount = (V1ServiceAccount) yaml("cluster/service-account.yaml");
			CheckedSupplier<V1ServiceAccount> accountSupplier = () -> coreV1Api.readNamespacedServiceAccount(
					serviceAccount.getMetadata().getName(), serviceAccountNamespace, null);
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
					.createNamespacedServiceAccount(serviceAccountNamespace, serviceAccount, null, null, null, null);
			notExistsHandler(accountSupplier, accountDefaulter);

			V1ClusterRole clusterRole = (V1ClusterRole) yaml("cluster/cluster-role.yaml");
			notExistsHandler(() -> rbacApi.readClusterRole(clusterRole.getMetadata().getName(), null),
					() -> rbacApi.createClusterRole(clusterRole, null, null, null, null));

			V1ClusterRoleBinding clusterRoleBinding = (V1ClusterRoleBinding) yaml("cluster/cluster-role-binding.yaml");
			notExistsHandler(() -> rbacApi.readClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), null),
					() -> rbacApi.createClusterRoleBinding(clusterRoleBinding, null, null, null, null));
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	public void deleteClusterWideClusterRoleBinding(String serviceAccountNamespace) {
		try {
			V1ServiceAccount serviceAccount = (V1ServiceAccount) yaml("cluster/service-account.yaml");
			V1ClusterRole clusterRole = (V1ClusterRole) yaml("cluster/cluster-role.yaml");
			V1ClusterRoleBinding clusterRoleBinding = (V1ClusterRoleBinding) yaml("cluster/cluster-role-binding.yaml");

			coreV1Api.deleteNamespacedServiceAccount(serviceAccount.getMetadata().getName(), serviceAccountNamespace,
					null, null, null, null, null, null);
			rbacApi.deleteClusterRole(clusterRole.getMetadata().getName(), null, null, null, null, null, null);
			rbacApi.deleteClusterRoleBinding(clusterRoleBinding.getMetadata().getName(), null, null, null, null, null,
					null);
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public void setUpClusterWide(String serviceAccountNamespace, Set<String> namespaces) {

		try {
			V1ServiceAccount serviceAccount = (V1ServiceAccount) yaml("cluster/service-account.yaml");
			CheckedSupplier<V1ServiceAccount> accountSupplier = () -> coreV1Api.readNamespacedServiceAccount(
					serviceAccount.getMetadata().getName(), serviceAccountNamespace, null);
			CheckedSupplier<V1ServiceAccount> accountDefaulter = () -> coreV1Api
					.createNamespacedServiceAccount(serviceAccountNamespace, serviceAccount, null, null, null, null);
			notExistsHandler(accountSupplier, accountDefaulter);

			V1ClusterRole clusterRole = (V1ClusterRole) yaml("cluster/cluster-role.yaml");
			notExistsHandler(() -> rbacApi.readClusterRole(clusterRole.getMetadata().getName(), null),
					() -> rbacApi.createClusterRole(clusterRole, null, null, null, null));

			V1RoleBinding roleBinding = (V1RoleBinding) yaml("cluster/role-binding.yaml");
			namespaces.forEach(namespace -> {
				roleBinding.getMetadata().setNamespace(namespace);
				try {
					notExistsHandler(
							() -> rbacApi.readNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace,
									null),
							() -> rbacApi.createNamespacedRoleBinding(namespace, roleBinding, null, null, null, null));
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

			coreV1Api.deleteNamespacedServiceAccount(serviceAccount.getMetadata().getName(), serviceAccountNamespace,
					null, null, null, null, null, null);
			rbacApi.deleteClusterRole(clusterRole.getMetadata().getName(), null, null, null, null, null, null);
			namespaces.forEach(namespace -> {
				roleBinding.getMetadata().setNamespace(namespace);
				try {
					rbacApi.deleteNamespacedRoleBinding(roleBinding.getMetadata().getName(), namespace, null, null,
							null, null, null, null);
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
		try {
			coreV1Api.deleteNamespace(name, null, null, null, null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}

		await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS)
				.until(() -> coreV1Api.listNamespace(null, null, null, null, null, null, null, null, null, null, null)
						.getItems().stream().noneMatch(x -> x.getMetadata().getName().equals(name)));
	}

	public void wiremock(String namespace, String path, Phase phase) {
		wiremock(namespace, path, phase, true);
	}

	public void wiremock(String namespace, String path, Phase phase, boolean withIngress) {
		V1Deployment deployment = (V1Deployment) yaml("wiremock/wiremock-deployment.yaml");
		V1Service service = (V1Service) yaml("wiremock/wiremock-service.yaml");

		V1Ingress ingress = null;

		if (phase.equals(Phase.CREATE)) {

			if (withIngress) {
				ingress = (V1Ingress) yaml("wiremock/wiremock-ingress.yaml");
				ingress.getMetadata().setNamespace(namespace);
				ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).setPath(path);
			}

			deployment.getMetadata().setNamespace(namespace);
			service.getMetadata().setNamespace(namespace);
			createAndWait(namespace, "wiremock", deployment, service, ingress, false);
		}
		else {
			if (withIngress) {
				ingress = (V1Ingress) yaml("wiremock/wiremock-ingress.yaml");
			}
			deleteAndWait(namespace, deployment, service, ingress);
		}

	}

	public static void patchWithMerge(String deploymentName, String namespace, String patchBody,
			Map<String, String> podLabels) {
		try {
			PatchUtils.patch(V1Deployment.class,
					() -> new AppsV1Api().patchNamespacedDeploymentCall(deploymentName, namespace,
							new V1Patch(patchBody), null, null, null, null, null, null),
					V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH, new CoreV1Api().getApiClient());
		}
		catch (ApiException e) {
			LOG.error("error : " + e.getResponseBody());
			throw new RuntimeException(e);
		}

		waitForDeploymentAfterPatch(deploymentName, namespace, podLabels);
	}

	public static void patchWithReplace(String imageName, String deploymentName, String namespace, String patchBody,
			Map<String, String> podLabels) {
		String body = patchBody.replace("image_name_here", imageName);

		try {
			PatchUtils.patch(V1Deployment.class,
					() -> new AppsV1Api().patchNamespacedDeploymentCall(deploymentName, namespace, new V1Patch(body),
							null, null, null, null, null, null),
					V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH, new CoreV1Api().getApiClient());
		}
		catch (ApiException e) {
			LOG.error("error : " + e.getResponseBody());
			throw new RuntimeException(e);
		}

		waitForDeploymentAfterPatch(deploymentName, namespace, podLabels);

	}

	private String deploymentName(V1Deployment deployment) {
		return deployment.getMetadata().getName();
	}

	private String serviceName(V1Service service) {
		return service.getMetadata().getName();
	}

	private String ingressName(V1Ingress ingress) {
		return ingress.getMetadata().getName();
	}

	private String configMapName(V1ConfigMap configMap) {
		return configMap.getMetadata().getName();
	}

	private String secretName(V1Secret secret) {
		return secret.getMetadata().getName();
	}

	private void waitForDeployment(String namespace, V1Deployment deployment) {
		String deploymentName = deploymentName(deployment);
		await().pollDelay(Duration.ofSeconds(5)).pollInterval(Duration.ofSeconds(5)).atMost(900, TimeUnit.SECONDS)
				.until(() -> isDeploymentReady(deploymentName, namespace));
	}

	private void waitForConfigMap(String namespace, V1ConfigMap configMap, Phase phase) {
		String configMapName = configMapName(configMap);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).until(() -> {
			try {
				coreV1Api.readNamespacedConfigMap(configMapName, namespace, null);
				return phase.equals(Phase.CREATE);
			}
			catch (ApiException e) {
				if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					return !phase.equals(Phase.CREATE);

				}
				throw new RuntimeException(e);
			}
		});
	}

	private void waitForSecret(String namespace, V1Secret secret, Phase phase) {
		String secretName = secretName(secret);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).until(() -> {
			try {
				coreV1Api.readNamespacedSecret(secretName, namespace, null);
				return phase.equals(Phase.CREATE);
			}
			catch (ApiException e) {
				if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					return !phase.equals(Phase.CREATE);
				}
				throw new RuntimeException(e);
			}
		});
	}

	private void waitForIngress(String namespace, V1Ingress ingress) {
		String ingressName = ingressName(ingress);
		await().timeout(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(3)).until(() -> {
			try {
				V1IngressLoadBalancerStatus status = networkingV1Api.readNamespacedIngress(ingressName, namespace, null)
						.getStatus().getLoadBalancer();

				if (status == null) {
					LOG.info("ingress : " + ingressName + " not ready yet (loadbalancer not yet present)");
					return false;
				}

				List<V1IngressLoadBalancerIngress> loadBalancerIngress = status.getIngress();
				if (loadBalancerIngress == null) {
					LOG.info("ingress : " + ingressName + " not ready yet (loadbalancer ingress not yet present)");
					return false;
				}

				String ip = loadBalancerIngress.get(0).getIp();
				if (ip == null) {
					LOG.info("ingress : " + ingressName + " not ready yet");
					return false;
				}

				LOG.info("ingress : " + ingressName + " ready with ip : " + ip);
				return true;
			}
			catch (ApiException e) {
				if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
					return false;
				}
				throw new RuntimeException(e);
			}
		});
	}

	private void waitForDeploymentToBeDeleted(String deploymentName, String namespace) {
		await().timeout(Duration.ofSeconds(180)).until(() -> {
			try {
				appsV1Api.readNamespacedDeployment(deploymentName, namespace, null);
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
		await().timeout(Duration.ofSeconds(180)).until(() -> {
			try {
				int currentNumberOfPods = coreV1Api.listNamespacedPod(namespace, null, null, null, null,
						labelSelector(labels), null, null, null, null, null, null).getItems().size();
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

	private void waitForIngressToBeDeleted(String ingressName, String namespace) {
		await().timeout(Duration.ofSeconds(90)).until(() -> {
			try {
				networkingV1Api.readNamespacedIngress(ingressName, namespace, null);
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

	private boolean isDeploymentReady(String deploymentName, String namespace) throws ApiException {
		V1DeploymentList deployments = appsV1Api.listNamespacedDeployment(namespace, null, null, null,
				"metadata.name=" + deploymentName, null, null, null, null, null, null, null);
		if (deployments.getItems().isEmpty()) {
			fail("No deployments with the name " + deploymentName);
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

	private static void waitForDeploymentAfterPatch(String deploymentName, String namespace,
			Map<String, String> podLabels) {
		try {
			await().pollDelay(Duration.ofSeconds(4)).pollInterval(Duration.ofSeconds(3)).atMost(60, TimeUnit.SECONDS)
					.until(() -> isDeploymentReadyAfterPatch(deploymentName, namespace, podLabels));
		}
		catch (Exception e) {
			if (e instanceof ApiException apiException) {
				LOG.error("Error: ");
				LOG.error(apiException.getResponseBody());
			}
			throw new RuntimeException(e);
		}

	}

	private static boolean isDeploymentReadyAfterPatch(String deploymentName, String namespace,
			Map<String, String> podLabels) throws ApiException {

		V1DeploymentList deployments = new AppsV1Api().listNamespacedDeployment(namespace, null, null, null,
				"metadata.name=" + deploymentName, null, null, null, null, null, null, null);
		if (deployments.getItems().isEmpty()) {
			fail("No deployment with name " + deploymentName);
		}

		V1Deployment deployment = deployments.getItems().get(0);
		// if no replicas are defined, it means only 1 is needed
		int replicas = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(1);
		int readyReplicas = Optional.ofNullable(deployment.getStatus().getReadyReplicas()).orElse(0);

		if (readyReplicas != replicas) {
			LOG.info("ready replicas not yet same as replicas");
			return false;
		}

		int pods = new CoreV1Api().listNamespacedPod(namespace, null, null, null, null, labelSelector(podLabels), null,
				null, null, null, null, null).getItems().size();

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
