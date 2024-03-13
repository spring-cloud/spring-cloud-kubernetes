/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.discovery;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_FIVE;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_FOUR;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_ONE;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_ONE_WITH_BOOTSTRAP;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_SEVEN;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_SIX;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_THREE;
import static org.springframework.cloud.kubernetes.fabric8.client.discovery.Fabric8DiscoveryClientUtil.BODY_TWO;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;

/**
 * @author wind57
 */
class Fabric8DiscoveryPodMetadataIT {

	private static final String DEPLOYMENT_NAME = "spring-cloud-kubernetes-fabric8-client-discovery-deployment";

	private static final String NAMESPACE = "default";

	private static final String NAMESPACE_A_UAT = "a-uat";

	private static final String NAMESPACE_B_UAT = "b-uat";

	private static final String NAMESPACE_LEFT = "namespace-left";

	private static final String NAMESPACE_RIGHT = "namespace-right";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-discovery";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + pomVersion();

	private static KubernetesClient client;

	private static Util util;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();

		util.setUp(NAMESPACE);

		manifests(Phase.CREATE);
		util.wiremock(NAMESPACE, "/wiremock", Phase.CREATE, false);
		util.busybox(NAMESPACE, Phase.CREATE);

		util.createNamespace(NAMESPACE_A_UAT);
		util.createNamespace(NAMESPACE_B_UAT);
		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.CREATE, false);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.CREATE, false);

		util.createNamespace(NAMESPACE_LEFT);
		util.createNamespace(NAMESPACE_RIGHT);
		util.wiremock(NAMESPACE_LEFT, "/wiremock", Phase.CREATE, false);
		util.wiremock(NAMESPACE_RIGHT, "/wiremock", Phase.CREATE, false);
	}

	@AfterAll
	static void after() throws Exception {
		util.wiremock(NAMESPACE, "/wiremock", Phase.DELETE, false);
		util.busybox(NAMESPACE, Phase.DELETE);

		util.wiremock(NAMESPACE_A_UAT, "/wiremock", Phase.DELETE, false);
		util.wiremock(NAMESPACE_B_UAT, "/wiremock", Phase.DELETE, false);
		util.deleteNamespace(NAMESPACE_A_UAT);
		util.deleteNamespace(NAMESPACE_B_UAT);

		util.wiremock(NAMESPACE_LEFT, "/wiremock", Phase.DELETE, false);
		util.wiremock(NAMESPACE_RIGHT, "/wiremock", Phase.DELETE, false);
		util.deleteNamespace(NAMESPACE_LEFT);
		util.deleteNamespace(NAMESPACE_RIGHT);

		manifests(Phase.DELETE);
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();
	}

	@Test
	void testPodMetadata() throws Exception {

		// find both pods
		String[] both = K3S.execInContainer("sh", "-c", "kubectl get pods -l app=busybox -o=name --no-headers")
				.getStdout().split("\n");
		// add a label to first pod
		K3S.execInContainer("sh", "-c",
				"kubectl label pods " + both[0].split("/")[1] + " custom-label=custom-label-value");
		// add annotation to the second pod
		K3S.execInContainer("sh", "-c",
				"kubectl annotate pods " + both[1].split("/")[1] + " custom-annotation=custom-annotation-value");

		WebClient client = builder().baseUrl("http://localhost/service-instances/busybox-service").build();
		List<DefaultKubernetesServiceInstance> serviceInstances = client.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		DefaultKubernetesServiceInstance withCustomLabel = serviceInstances.stream()
				.filter(x -> x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty()).toList().get(0);
		Assertions.assertEquals(withCustomLabel.getServiceId(), "busybox-service");
		Assertions.assertNotNull(withCustomLabel.getInstanceId());
		Assertions.assertNotNull(withCustomLabel.getHost());
		Assertions.assertEquals(withCustomLabel.getMetadata(),
				Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
		Assertions.assertTrue(withCustomLabel.podMetadata().get("labels").entrySet().stream()
				.anyMatch(x -> x.getKey().equals("custom-label") && x.getValue().equals("custom-label-value")));

		DefaultKubernetesServiceInstance withCustomAnnotation = serviceInstances.stream()
				.filter(x -> !x.podMetadata().getOrDefault("annotations", Map.of()).isEmpty()).toList().get(0);
		Assertions.assertEquals(withCustomAnnotation.getServiceId(), "busybox-service");
		Assertions.assertNotNull(withCustomAnnotation.getInstanceId());
		Assertions.assertNotNull(withCustomAnnotation.getHost());
		Assertions.assertEquals(withCustomAnnotation.getMetadata(),
				Map.of("k8s_namespace", "default", "type", "ClusterIP", "port.busybox-port", "80"));
		Assertions.assertTrue(withCustomAnnotation.podMetadata().get("annotations").entrySet().stream().anyMatch(
				x -> x.getKey().equals("custom-annotation") && x.getValue().equals("custom-annotation-value")));

		testAllOther();
	}

	private void testAllOther() {
		testAllServices();
		testAllServicesWithBootstrap();
		testExternalNameServiceInstance();
		testBlockingConfiguration();
		testDefaultConfiguration();
		testReactiveConfiguration();
		filterMatchesBothNamespacesViaThePredicate();
		filterMatchesOneNamespaceViaThePredicate();
		namespaceFilter();
	}

	private void testAllServices() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_ONE, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryDelegate.testAllServices();
	}

	private void testAllServicesWithBootstrap() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_ONE_WITH_BOOTSTRAP,
				Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryBoostrapDelegate.testAllServicesWithBootstrap();
	}

	private void testExternalNameServiceInstance() {
		Fabric8DiscoveryDelegate.testExternalNameServiceInstance();
	}

	private void testBlockingConfiguration() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_TWO, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryClientHealthDelegate.testBlockingConfiguration(K3S, IMAGE_NAME);
	}

	private void testDefaultConfiguration() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_THREE, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryClientHealthDelegate.testDefaultConfiguration(K3S, IMAGE_NAME);
	}

	private void testReactiveConfiguration() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_FOUR, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryClientHealthDelegate.testReactiveConfiguration(K3S, IMAGE_NAME);
	}

	private void filterMatchesBothNamespacesViaThePredicate() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_FIVE, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryFilterDelegate.filterMatchesBothNamespacesViaThePredicate();
	}

	private void filterMatchesOneNamespaceViaThePredicate() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_SIX, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryFilterDelegate.filterMatchesOneNamespaceViaThePredicate();
	}

	private void namespaceFilter() {
		util.patchWithReplace(DOCKER_IMAGE, DEPLOYMENT_NAME, NAMESPACE, BODY_SEVEN, Map.of("app", IMAGE_NAME));
		Fabric8DiscoveryNamespaceDelegate.namespaceFilter();
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("fabric8-discovery-deployment.yaml");
		InputStream externalNameServiceStream = util.inputStream("external-name-service.yaml");
		InputStream discoveryServiceStream = util.inputStream("fabric8-discovery-service.yaml");
		InputStream ingressStream = util.inputStream("fabric8-discovery-ingress.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		List<EnvVar> existing = new ArrayList<>(
				deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv());
		existing.add(new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDPODLABELS")
				.withValue("true").build());
		existing.add(new EnvVarBuilder().withName("SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDPODANNOTATIONS")
				.withValue("true").build());
		existing.add(
				new EnvVarBuilder().withName("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY")
						.withValue("DEBUG").build());
		deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(existing);

		Service externalServiceName = Serialization.unmarshal(externalNameServiceStream, Service.class);
		Service discoveryService = Serialization.unmarshal(discoveryServiceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);

		ClusterRoleBinding clusterRoleBinding = Serialization.unmarshal(getAdminRole(), ClusterRoleBinding.class);
		if (phase.equals(Phase.CREATE)) {
			client.rbac().clusterRoleBindings().resource(clusterRoleBinding).create();
			util.createAndWait(NAMESPACE, IMAGE_NAME, deployment, discoveryService, ingress, true);
			util.createAndWait(NAMESPACE, null, null, externalServiceName, null, true);
		}
		else {
			client.rbac().clusterRoleBindings().resource(clusterRoleBinding).delete();
			util.deleteAndWait(NAMESPACE, deployment, discoveryService, ingress);
			util.deleteAndWait(NAMESPACE, null, externalServiceName, null);
		}

	}

	private static InputStream getAdminRole() {
		return util.inputStream("namespace-filter/fabric8-cluster-admin-serviceaccount-role.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
