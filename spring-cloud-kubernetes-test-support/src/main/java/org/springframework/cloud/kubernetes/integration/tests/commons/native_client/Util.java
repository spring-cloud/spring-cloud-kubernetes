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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
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
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.fail;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.loadImage;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pullImage;

public final class Util {

	private static final Log LOG = LogFactory.getLog(Util.class);

	private static final String KUBERNETES_VERSION_FILE = "META-INF/springcloudkubernetes-version.txt";

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

			String imageFromDeployment = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
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
			coreV1Api.createNamespacedService(namespace, service, null, null, null, null);
			waitForDeployment(namespace, deployment);
			if (ingress != null) {
				networkingV1Api.createNamespacedIngress(namespace, ingress, null, null, null, null);
				waitForIngress(namespace, ingress);
			}
		}
		catch (Exception e) {
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
		String deploymentName = deploymentName(deployment);
		String serviceName = serviceName(service);
		try {
			appsV1Api.deleteNamespacedDeployment(deploymentName, namespace, null, null, null, null, null, null);
			coreV1Api.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null);
			waitForDeploymentToBeDeleted(deploymentName, namespace);

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

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "kafka", deployment, service, null, false);
		}
		else if (phase.equals(Phase.DELETE)) {
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

	public void zookeeper(String namespace, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("zookeeper/zookeeper-deployment.yaml");
		V1Service service = (V1Service) yaml("zookeeper/zookeeper-service.yaml");

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "zookeeper", deployment, service, null, false);
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

	public void deleteNamespace(String name) {
		try {
			coreV1Api.deleteNamespace(name, null, null, null, null, null, null);
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}

		await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS)
				.until(() -> coreV1Api.listNamespace(null, null, null, null, null, null, null, null, null, null)
						.getItems().stream().noneMatch(x -> x.getMetadata().getName().equals(name)));
	}

	public void wiremock(String namespace, boolean rootPath, Phase phase) {
		V1Deployment deployment = (V1Deployment) yaml("wiremock/wiremock-deployment.yaml");
		V1Service service = (V1Service) yaml("wiremock/wiremock-service.yaml");
		V1Ingress ingress;
		if (rootPath) {
			ingress = (V1Ingress) yaml("wiremock/wiremock-root-path-ingress.yaml");
		}
		else {
			ingress = (V1Ingress) yaml("wiremock/wiremock-ingress.yaml");
		}

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "wiremock", deployment, service, ingress, false);
		}
		else {
			deleteAndWait(namespace, deployment, service, ingress);
		}

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

	private String pomVersion() {
		try (InputStream in = new ClassPathResource(KUBERNETES_VERSION_FILE).getInputStream()) {
			String version = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
			if (StringUtils.hasText(version)) {
				version = version.trim();
			}
			return version;
		}
		catch (IOException e) {
			ReflectionUtils.rethrowRuntimeException(e);
		}
		// not reachable since exception rethrown at runtime
		return null;
	}

	private void waitForDeployment(String namespace, V1Deployment deployment) {
		String deploymentName = deploymentName(deployment);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS)
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
				V1LoadBalancerStatus status = networkingV1Api.readNamespacedIngress(ingressName, namespace, null)
						.getStatus().getLoadBalancer();

				if (status == null) {
					LOG.info("ingress : " + ingressName + " not ready yet (loadbalancer not yet present)");
					return false;
				}

				List<V1LoadBalancerIngress> loadBalancerIngress = status.getIngress();
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
		await().timeout(Duration.ofSeconds(90)).until(() -> {
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
				"metadata.name=" + deploymentName, null, null, null, null, null, null);
		if (deployments.getItems().size() < 1) {
			fail("No deployments with the name " + deploymentName);
		}
		V1Deployment deployment = deployments.getItems().get(0);
		Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
		LOG.info("Available replicas for " + deploymentName + ": "
				+ (availableReplicas == null ? 0 : availableReplicas));
		return availableReplicas != null && availableReplicas >= 1;
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

	private interface CheckedSupplier<T> {

		T get() throws Exception;

	}

}
