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

package org.springframework.cloud.kubernetes.fabric8.configmap.event.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.HasMetadataOperation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Fabric8Utils;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.processExecResult;

/**
 * @author wind57
 */
class ConfigMapEventReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-configmap-event-reload";

	private static final String NAMESPACE = "default";

	private static KubernetesClient client;

	private static String deploymentName;

	private static String serviceName;

	private static String ingressName;

	private static String leftConfigMapName;

	private static String rightConfigMapName;

	private static String rightWithLabelConfigMapName;

	private static final K3sContainer K3S = Commons.container();

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		client = new DefaultKubernetesClient(config);

		createNamespaces();
		Fabric8Utils.setUpClusterWide(client, "default", Set.of("left", "right"));
	}

	@AfterAll
	static void afterAll() throws Exception {
		deleteNamespaces();
		Commons.cleanUp(IMAGE_NAME, K3S);
	}

	/**
	 * <pre>
	 *     - there are two namespaces : left and right
	 *     - each of the namespaces has one configmap
	 *     - we watch the "left" namespace, but make a change in the configmap in the right namespace
	 *     - as such, no event is triggered and "left-configmap" stays as-is
	 * </pre>
	 */
	@Test
	void testInformFromOneNamespaceEventNotTriggered() {
		deployManifests("one");

		WebClient webClient = builder().baseUrl("localhost/left").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the left-configmap
		Assertions.assertEquals("left-initial", result);

		// then read the value from the right-configmap
		webClient = builder().baseUrl("localhost/right").build();
		result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-initial", result);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange, "right-configmap");

		// wait dummy for 5 seconds
		LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));

		webClient = builder().baseUrl("localhost/left").build();
		result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		// left configmap has not changed, no restart of app has happened
		Assertions.assertEquals("left-initial", result);

		deleteManifests();
	}

	/**
	 * <pre>
	 *     - there are two namespaces : left and right
	 *     - each of the namespaces has one configmap
	 *     - we watch the "right" namespace and make a change in the configmap in the same namespace
	 *     - as such, event is triggered and we see the updated value
	 * </pre>
	 */
	@Test
	void testInformFromOneNamespaceEventTriggered() {
		deployManifests("two");

		// read the value from the right-configmap
		WebClient webClient = builder().baseUrl("localhost/right").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", result);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange, "right-configmap");

		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("localhost/right").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			resultAfterChange[0] = innerResult;
			return innerResult != null;
		});
		Assertions.assertEquals("right-after-change", resultAfterChange[0]);

		deleteManifests();
	}

	/**
	 * <pre>
	 *     - there are two namespaces : left and right (though we do not care about the left one)
	 *     - left has one configmap : left-configmap
	 *     - right has two configmaps: right-configmap, right-configmap-with-label
	 *     - we watch the "right" namespace, but enable tagging; which means that only
	 *       right-configmap-with-label triggers changes.
	 * </pre>
	 */
	@Test
	void testInform() {
		deployManifests("three");

		// read the initial value from the right-configmap
		WebClient rightWebClient = builder().baseUrl("localhost/right").build();
		String rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-initial", rightResult);

		// then read the initial value from the right-with-label-configmap
		WebClient rightWithLabelWebClient = builder().baseUrl("localhost/with-label").build();
		String rightWithLabelResult = rightWithLabelWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertEquals("right-with-label-initial", rightWithLabelResult);

		// then deploy a new version of right-configmap
		ConfigMap rightConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("right").withName("right-configmap").build())
				.withData(Map.of("right.value", "right-after-change")).build();

		replaceConfigMap(rightConfigMapAfterChange, "right-configmap");

		// sleep for 5 seconds
		LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));

		// nothing changes in our app, because we are watching only labeled configmaps
		rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-initial", rightResult);

		// then deploy a new version of right-with-label-configmap
		ConfigMap rightWithLabelConfigMapAfterChange = new ConfigMapBuilder()
				.withMetadata(
						new ObjectMetaBuilder().withNamespace("right").withName("right-configmap-with-label").build())
				.withData(Map.of("right.with.label.value", "right-with-label-after-change")).build();

		replaceConfigMap(rightWithLabelConfigMapAfterChange, "right-configmap-with-label");

		// since we have changed a labeled configmap, app will restart and pick up the new
		// value
		String[] resultAfterChange = new String[1];
		await().pollInterval(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(90)).until(() -> {
			WebClient innerWebClient = builder().baseUrl("localhost/with-label").build();
			String innerResult = innerWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
					.retryWhen(retrySpec()).block();
			resultAfterChange[0] = innerResult;
			return innerResult != null;
		});
		Assertions.assertEquals("right-with-label-after-change", resultAfterChange[0]);

		// right-configmap now will see the new value also, but only because the other
		// configmap has triggered the restart
		rightResult = rightWebClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();
		Assertions.assertEquals("right-after-change", rightResult);

		deleteManifests();
	}

	private static void createNamespaces() throws Exception {
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl create namespace left"));
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl create namespace right"));
	}

	private static void deleteNamespaces() throws Exception {
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl delete namespace left"));
		processExecResult(K3S.execInContainer("sh", "-c", "kubectl delete namespace right"));
	}

	private static void deployManifests(String deploymentRoot) {

		try {

			ConfigMap leftConfigMap = client.configMaps().load(leftConfigMap()).get();
			leftConfigMapName = leftConfigMap.getMetadata().getName();
			client.configMaps().create(leftConfigMap);

			ConfigMap rightConfigMap = client.configMaps().load(rightConfigMap()).get();
			rightConfigMapName = rightConfigMap.getMetadata().getName();
			client.configMaps().create(rightConfigMap);

			if ("three".equals(deploymentRoot)) {
				ConfigMap rightWithLabelConfigMap = client.configMaps().load(rightWithLabelConfigMap()).get();
				rightWithLabelConfigMapName = rightWithLabelConfigMap.getMetadata().getName();
				client.configMaps().create(rightWithLabelConfigMap);
			}

			Deployment deployment = client.apps().deployments().load(getDeployment(deploymentRoot)).get();

			String version = K8SUtils.getPomVersion();
			String currentImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
			deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(currentImage + ":" + version);

			client.apps().deployments().inNamespace(NAMESPACE).create(deployment);
			deploymentName = deployment.getMetadata().getName();

			Service service = client.services().load(getService()).get();
			serviceName = service.getMetadata().getName();
			client.services().inNamespace(NAMESPACE).create(service);

			Ingress ingress = client.network().v1().ingresses().load(getIngress()).get();
			ingressName = ingress.getMetadata().getName();
			client.network().v1().ingresses().inNamespace(NAMESPACE).create(ingress);

			Fabric8Utils.waitForDeployment(client,
					"spring-cloud-kubernetes-fabric8-client-configmap-deployment-event-reload", NAMESPACE, 2, 600);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void deleteManifests() {

		try {

			client.configMaps().inNamespace("left").withName(leftConfigMapName).delete();
			Fabric8Utils.waitForConfigMapDelete(client, "left", leftConfigMapName);
			client.configMaps().inNamespace("right").withName(rightConfigMapName).delete();
			Fabric8Utils.waitForConfigMapDelete(client, "right", rightConfigMapName);
			if (rightWithLabelConfigMapName != null) {
				client.configMaps().inNamespace("right").withName(rightWithLabelConfigMapName).delete();
				Fabric8Utils.waitForConfigMapDelete(client, "right", rightWithLabelConfigMapName);
			}
			client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).delete();
			client.services().inNamespace(NAMESPACE).withName(serviceName).delete();
			client.network().v1().ingresses().inNamespace(NAMESPACE).withName(ingressName).delete();

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static InputStream leftConfigMap() {
		return Fabric8Utils.inputStream("left-configmap.yaml");
	}

	private static InputStream rightConfigMap() {
		return Fabric8Utils.inputStream("right-configmap.yaml");
	}

	private static InputStream rightWithLabelConfigMap() {
		return Fabric8Utils.inputStream("right-configmap-with-label.yaml");
	}

	private static InputStream getDeployment(String root) {
		return Fabric8Utils.inputStream(root + "/deployment.yaml");
	}

	private static InputStream getService() {
		return Fabric8Utils.inputStream("service.yaml");
	}

	private static InputStream getIngress() {
		return Fabric8Utils.inputStream("ingress.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

	// the weird cast comes from :
	// https://github.com/fabric8io/kubernetes-client/issues/2445
	@SuppressWarnings({ "unchecked", "raw" })
	private static void replaceConfigMap(ConfigMap configMap, String name) {
		((HasMetadataOperation) client.configMaps().inNamespace("right").withName(name)).createOrReplace(configMap);
	}

}
