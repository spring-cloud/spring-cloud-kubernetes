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

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
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

	public static InputStream inputStream(String fileName) {
		return Fabric8Utils.class.getClassLoader().getResourceAsStream(fileName);
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
		LOG.info("Available replicas for " + deploymentName + ": " + ((availableReplicas == null) ? 0 : 1));
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
		InputStream serviceAccountAsStream = inputStream("setup/service-account.yaml");
		InputStream roleBindingAsStream = inputStream("setup/role-binding.yaml");
		InputStream roleAsStream = inputStream("setup/role.yaml");

		innerSetup(client, namespace, serviceAccountAsStream, roleBindingAsStream, roleAsStream);
	}

	public static void setUpClusterWide(KubernetesClient client, String serviceAccountNamespace,
			Set<String> namespaces) {
		InputStream clusterRoleBindingAsStream = inputStream("cluster/cluster-role.yaml");
		InputStream serviceAccountAsStream = inputStream("cluster/service-account.yaml");
		InputStream roleBindingAsStream = inputStream("cluster/role-binding.yaml");

		ClusterRole clusterRole = client.rbac().clusterRoles().load(clusterRoleBindingAsStream).get();
		if (client.rbac().clusterRoles().withName(clusterRole.getMetadata().getName()).get() == null) {
			client.rbac().clusterRoles().create(clusterRole);
		}

		ServiceAccount serviceAccountFromStream = client.serviceAccounts().load(serviceAccountAsStream).get();
		serviceAccountFromStream.getMetadata().setNamespace(serviceAccountNamespace);
		if (client.serviceAccounts().inNamespace(serviceAccountNamespace)
				.withName(serviceAccountFromStream.getMetadata().getName()).get() == null) {
			client.serviceAccounts().inNamespace(serviceAccountNamespace).create(serviceAccountFromStream);
		}

		RoleBinding roleBindingFromStream = client.rbac().roleBindings().load(roleBindingAsStream).get();
		namespaces.forEach(namespace -> {
			roleBindingFromStream.getMetadata().setNamespace(namespace);

			if (client.rbac().roleBindings().inNamespace(namespace)
					.withName(roleBindingFromStream.getMetadata().getName()).get() == null) {
				client.rbac().roleBindings().inNamespace(namespace).create(roleBindingFromStream);
			}
		});

	}

	public static void setUpIstio(KubernetesClient client, String namespace) {
		InputStream serviceAccountAsStream = inputStream("istio/service-account.yaml");
		InputStream roleBindingAsStream = inputStream("istio/role-binding.yaml");
		InputStream roleAsStream = inputStream("istio/role.yaml");

		innerSetup(client, namespace, serviceAccountAsStream, roleBindingAsStream, roleAsStream);
	}

	public static void waitForIngress(KubernetesClient client, String ingressName, String namespace) {

		try {
			await().pollInterval(Duration.ofSeconds(2)).atMost(180, TimeUnit.SECONDS).until(() -> {
				Ingress ingress = client.network().v1().ingresses().inNamespace(namespace).withName(ingressName).get();

				if (ingress == null) {
					System.out.println("ingress : " + ingressName + " not ready yet present");
					return false;
				}

				List<LoadBalancerIngress> loadBalancerIngress = ingress.getStatus().getLoadBalancer().getIngress();
				if (loadBalancerIngress == null || loadBalancerIngress.isEmpty()) {
					System.out.println(
							"ingress : " + ingressName + " not ready yet (loadbalancer ingress not yet present)");
					return false;
				}

				String ip = loadBalancerIngress.get(0).getIp();
				if (ip == null) {
					System.out.println("ingress : " + ingressName + " not ready yet");
					return false;
				}

				System.out.println("ingress : " + ingressName + " ready with ip : " + ip);
				return true;

			});
		}
		catch (Exception e) {
			System.out.println("Error waiting for ingress");
			e.printStackTrace();
		}

	}

	public static void waitForConfigMapDelete(KubernetesClient client, String namespace, String name) {
		await().pollInterval(Duration.ofSeconds(1)).atMost(30, TimeUnit.SECONDS).until(() -> {
			ConfigMap configMap = client.configMaps().inNamespace(namespace).withName(name).get();
			return configMap == null;
		});
	}

	private static void innerSetup(KubernetesClient client, String namespace, InputStream serviceAccountAsStream,
			InputStream roleBindingAsStream, InputStream roleAsStream) {
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
