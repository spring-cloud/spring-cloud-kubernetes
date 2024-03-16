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

package org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerIngress;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
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

	private final K3sContainer container;

	private final KubernetesClient client;

	public Util(K3sContainer container) {
		this.container = container;
		this.client = new KubernetesClientBuilder().withConfig(Config.fromKubeconfig(container.getKubeConfigYaml()))
				.build();
	}

	/**
	 * This is the preferred method to use when creating a deployment alongside with a
	 * service. It creates the given resources as-well as waits for them to be created.
	 * The delay check is intentionally not taken as an argument, so that it stays as
	 * tight as possible, providing reasonable defaults.
	 *
	 */
	public void createAndWait(String namespace, String name, @Nullable Deployment deployment, Service service,
			@Nullable Ingress ingress, boolean changeVersion) {
		try {

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

				client.apps().deployments().inNamespace(namespace).resource(deployment).create();
				waitForDeployment(namespace, deployment);
			}

			client.services().inNamespace(namespace).resource(service).create();

			if (ingress != null) {
				client.network().v1().ingresses().inNamespace(namespace).resource(ingress).create();
				waitForIngress(namespace, ingress);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void busybox(String namespace, Phase phase) {
		InputStream deploymentStream = inputStream("busybox/deployment.yaml");
		InputStream serviceStream = inputStream("busybox/service.yaml");
		Deployment deployment = client.apps().deployments().load(deploymentStream).item();
		Service service = client.services().load(serviceStream).item();

		if (phase.equals(Phase.CREATE)) {
			createAndWait(namespace, "busybox", deployment, service, null, false);
		}
		else if (phase.equals(Phase.DELETE)) {
			deleteAndWait(namespace, deployment, service, null);
		}
	}

	public void deleteAndWait(String namespace, @Nullable Deployment deployment, Service service,
			@Nullable Ingress ingress) {
		try {

			if (deployment != null) {
				client.apps().deployments().inNamespace(namespace).resource(deployment).delete();
				waitForDeploymentToBeDeleted(namespace, deployment);
			}

			client.services().inNamespace(namespace).resource(service).delete();

			if (ingress != null) {
				client.network().v1().ingresses().inNamespace(namespace).resource(ingress).delete();
				waitForIngressToBeDeleted(namespace, ingress);
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setUp(String namespace) throws Exception {
		InputStream serviceAccountAsStream = inputStream("setup/service-account.yaml");
		InputStream roleBindingAsStream = inputStream("setup/role-binding.yaml");
		InputStream roleAsStream = inputStream("setup/role.yaml");

		innerSetup(namespace, serviceAccountAsStream, roleBindingAsStream, roleAsStream);
	}

	public InputStream inputStream(String fileName) {
		return Util.class.getClassLoader().getResourceAsStream(fileName);
	}

	public void createNamespace(String name) {
		try {
			client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(name).and().build())
					.create();

			await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS).until(() -> client.namespaces()
					.list().getItems().stream().anyMatch(x -> x.getMetadata().getName().equals(name)));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void deleteNamespace(String name) {
		try {
			client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(name).and().build())
					.delete();

			await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS).until(() -> client.namespaces()
					.list().getItems().stream().noneMatch(x -> x.getMetadata().getName().equals(name)));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public void setUpClusterWide(String serviceAccountNamespace, Set<String> namespaces) {
		InputStream clusterRoleBindingAsStream = inputStream("cluster/cluster-role.yaml");
		InputStream serviceAccountAsStream = inputStream("cluster/service-account.yaml");
		InputStream roleBindingAsStream = inputStream("cluster/role-binding.yaml");

		ClusterRole clusterRole = client.rbac().clusterRoles().load(clusterRoleBindingAsStream).item();
		if (client.rbac().clusterRoles().withName(clusterRole.getMetadata().getName()).get() == null) {
			client.rbac().clusterRoles().resource(clusterRole).create();
		}

		ServiceAccount serviceAccountFromStream = client.serviceAccounts().load(serviceAccountAsStream).item();
		serviceAccountFromStream.getMetadata().setNamespace(serviceAccountNamespace);
		if (client.serviceAccounts().inNamespace(serviceAccountNamespace)
				.withName(serviceAccountFromStream.getMetadata().getName()).get() == null) {
			client.serviceAccounts().inNamespace(serviceAccountNamespace).resource(serviceAccountFromStream).create();
		}

		RoleBinding roleBindingFromStream = client.rbac().roleBindings().load(roleBindingAsStream).item();
		namespaces.forEach(namespace -> {
			roleBindingFromStream.getMetadata().setNamespace(namespace);

			if (client.rbac().roleBindings().inNamespace(namespace)
					.withName(roleBindingFromStream.getMetadata().getName()).get() == null) {
				client.rbac().roleBindings().inNamespace(namespace).resource(roleBindingFromStream).create();
			}
		});

	}

	public void createAndWait(String namespace, @Nullable ConfigMap configMap, @Nullable Secret secret) {
		if (configMap != null) {
			client.configMaps().resource(configMap).create();
			waitForConfigMap(namespace, configMap, Phase.CREATE);
		}

		if (secret != null) {
			client.secrets().resource(secret).create();
			waitForSecret(namespace, secret, Phase.CREATE);
		}
	}

	public void deleteAndWait(String namespace, @Nullable ConfigMap configMap, @Nullable Secret secret) {
		if (configMap != null) {
			client.configMaps().resource(configMap).delete();
			waitForConfigMap(namespace, configMap, Phase.DELETE);
		}

		if (secret != null) {
			client.secrets().resource(secret).delete();
			waitForSecret(namespace, secret, Phase.DELETE);
		}
	}

	public void setUpIstio(String namespace) {
		InputStream serviceAccountAsStream = inputStream("istio/service-account.yaml");
		InputStream roleBindingAsStream = inputStream("istio/role-binding.yaml");
		InputStream roleAsStream = inputStream("istio/role.yaml");

		innerSetup(namespace, serviceAccountAsStream, roleBindingAsStream, roleAsStream);
	}

	private void waitForConfigMap(String namespace, ConfigMap configMap, Phase phase) {
		String configMapName = configMapName(configMap);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).until(() -> {
			int size = (int) client.configMaps().inNamespace(namespace).list().getItems().stream()
					.filter(x -> x.getMetadata().getName().equals(configMapName)).count();
			if (size == 0) {
				return !phase.equals(Phase.CREATE);
			}
			return phase.equals(Phase.CREATE);
		});
	}

	public void wiremock(String namespace, String path, Phase phase) {
		wiremock(namespace, path, phase, true);
	}

	public void wiremock(String namespace, String path, Phase phase, boolean withIngress) {
		InputStream deploymentStream = inputStream("wiremock/wiremock-deployment.yaml");
		InputStream serviceStream = inputStream("wiremock/wiremock-service.yaml");
		InputStream ingressStream = inputStream("wiremock/wiremock-ingress.yaml");

		Deployment deployment = client.apps().deployments().load(deploymentStream).item();
		Service service = client.services().load(serviceStream).item();
		Ingress ingress = null;

		if (phase.equals(Phase.CREATE)) {

			if (withIngress) {
				ingress = client.network().v1().ingresses().load(ingressStream).get();
				ingress.getMetadata().setNamespace(namespace);
				ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).setPath(path);
			}

			deployment.getMetadata().setNamespace(namespace);
			service.getMetadata().setNamespace(namespace);
			createAndWait(namespace, "wiremock", deployment, service, ingress, false);
		}
		else {

			if (withIngress) {
				ingress = client.network().v1().ingresses().load(ingressStream).get();
			}

			deleteAndWait(namespace, deployment, service, ingress);
		}

	}

	private void waitForSecret(String namespace, Secret secret, Phase phase) {
		String secretName = secretName(secret);
		await().pollInterval(Duration.ofSeconds(1)).atMost(600, TimeUnit.SECONDS).until(() -> {
			int size = (int) client.secrets().inNamespace(namespace).list().getItems().stream()
					.filter(x -> x.getMetadata().getName().equals(secretName)).count();
			if (size == 0) {
				return !phase.equals(Phase.CREATE);
			}
			return phase.equals(Phase.CREATE);
		});
	}

	private void waitForIngressToBeDeleted(String namespace, Ingress ingress) {
		String ingressName = ingressName(ingress);
		await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS).until(() -> {
			Ingress inner = client.network().v1().ingresses().inNamespace(namespace).withName(ingressName).get();
			return inner == null;
		});
	}

	private void waitForDeploymentToBeDeleted(String namespace, Deployment deployment) {

		String deploymentName = deploymentName(deployment);

		Map<String, String> matchLabels = deployment.getSpec().getSelector().getMatchLabels();

		await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS).until(() -> {
			Deployment inner = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
			return inner == null;
		});

		await().pollInterval(Duration.ofSeconds(1)).atMost(60, TimeUnit.SECONDS).until(() -> {
			List<Pod> podList = client.pods().inNamespace(namespace).withLabels(matchLabels).list().getItems();
			return podList == null || podList.isEmpty();
		});
	}

	private void waitForDeployment(String namespace, Deployment deployment) {
		String deploymentName = deploymentName(deployment);
		await().pollInterval(Duration.ofSeconds(2)).atMost(600, TimeUnit.SECONDS)
				.until(() -> isDeploymentReady(namespace, deploymentName));
	}

	private boolean isDeploymentReady(String namespace, String deploymentName) {

		Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();

		Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
		LOG.info("Available replicas for " + deploymentName + ": " + ((availableReplicas == null) ? 0 : 1));
		return availableReplicas != null && availableReplicas >= 1;
	}

	public void waitForIngress(String namespace, Ingress ingress) {

		String ingressName = ingressName(ingress);

		try {
			await().pollInterval(Duration.ofSeconds(2)).atMost(180, TimeUnit.SECONDS).until(() -> {
				Ingress inner = client.network().v1().ingresses().inNamespace(namespace).withName(ingressName).get();

				if (inner == null) {
					LOG.info("ingress : " + ingressName + " not ready yet present");
					return false;
				}

				List<IngressLoadBalancerIngress> loadBalancerIngress = inner.getStatus().getLoadBalancer().getIngress();
				if (loadBalancerIngress == null || loadBalancerIngress.isEmpty()) {
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

			});
		}
		catch (Exception e) {
			LOG.error("Error waiting for ingress");
			e.printStackTrace();
		}

	}

	public void patchWithReplace(String imageName, String deploymentName, String namespace, String patchBody,
			Map<String, String> labels) {
		String body = patchBody.replace("image_name_here", imageName);

		client.apps().deployments().inNamespace(namespace).withName(deploymentName)
				.patch(PatchContext.of(PatchType.JSON_MERGE), body);

		waitForDeploymentAfterPatch(deploymentName, namespace, labels);
	}

	private void waitForDeploymentAfterPatch(String deploymentName, String namespace, Map<String, String> labels) {
		try {
			await().pollDelay(Duration.ofSeconds(4)).pollInterval(Duration.ofSeconds(3)).atMost(60, TimeUnit.SECONDS)
					.until(() -> isDeploymentReadyAfterPatch(deploymentName, namespace, labels));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private boolean isDeploymentReadyAfterPatch(String deploymentName, String namespace, Map<String, String> labels) {

		DeploymentList deployments = client.apps().deployments().inNamespace(namespace).list();

		if (deployments.getItems().isEmpty()) {
			fail("No deployment with name " + deploymentName);
		}

		Deployment deployment = deployments.getItems().stream()
				.filter(x -> x.getMetadata().getName().equals(deploymentName)).findFirst().orElseThrow();
		// if no replicas are defined, it means only 1 is needed
		int replicas = Optional.ofNullable(deployment.getSpec().getReplicas()).orElse(1);

		int numberOfPods = client.pods().inNamespace(namespace).withLabels(labels).list().getItems().size();

		if (numberOfPods != replicas) {
			LOG.info("number of pods not yet stabilized");
			return false;
		}

		return replicas == Optional.ofNullable(deployment.getStatus().getReadyReplicas()).orElse(0);

	}

	private void innerSetup(String namespace, InputStream serviceAccountAsStream, InputStream roleBindingAsStream,
			InputStream roleAsStream) {
		ServiceAccount serviceAccountFromStream = client.serviceAccounts().inNamespace(namespace)
				.load(serviceAccountAsStream).item();
		if (client.serviceAccounts().inNamespace(namespace).withName(serviceAccountFromStream.getMetadata().getName())
				.get() == null) {
			client.serviceAccounts().inNamespace(namespace).resource(serviceAccountFromStream).create();
		}

		RoleBinding roleBindingFromStream = client.rbac().roleBindings().inNamespace(namespace)
				.load(roleBindingAsStream).item();
		if (client.rbac().roleBindings().inNamespace(namespace).withName(roleBindingFromStream.getMetadata().getName())
				.get() == null) {
			client.rbac().roleBindings().inNamespace(namespace).resource(roleBindingFromStream).create();
		}

		Role roleFromStream = client.rbac().roles().inNamespace(namespace).load(roleAsStream).item();
		if (client.rbac().roles().inNamespace(namespace).withName(roleFromStream.getMetadata().getName())
				.get() == null) {
			client.rbac().roles().inNamespace(namespace).resource(roleFromStream).create();
		}
	}

	private String deploymentName(Deployment deployment) {
		return deployment.getMetadata().getName();
	}

	private String ingressName(Ingress ingress) {
		return ingress.getMetadata().getName();
	}

	private String configMapName(ConfigMap configMap) {
		return configMap.getMetadata().getName();
	}

	private String secretName(Secret secret) {
		return secret.getMetadata().getName();
	}

	public KubernetesClient client() {
		return client;
	}

}
