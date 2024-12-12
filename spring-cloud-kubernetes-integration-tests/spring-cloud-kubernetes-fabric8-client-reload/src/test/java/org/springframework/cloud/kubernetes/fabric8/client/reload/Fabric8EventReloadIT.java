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

package org.springframework.cloud.kubernetes.fabric8.client.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;

/**
 * @author wind57
 */
class Fabric8EventReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-reload";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + pomVersion();

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static KubernetesClient client;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		util = new Util(K3S);
		client = util.client();

		util.createNamespace("left");
		util.createNamespace("right");
		util.setUpClusterWide(NAMESPACE, Set.of("left", "right"));
		util.setUp(NAMESPACE);

		manifests(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.deleteNamespace("left");
		util.deleteNamespace("right");
		Commons.cleanUp(IMAGE_NAME, K3S);
		Commons.systemPrune();

		manifests(Phase.DELETE);
	}

	@Test
	void testInformFromOneNamespaceEventNotTriggered() {
		testConfigMapMountPollingReload();
		testPollingReloadConfigMapWithBootstrap();
	}


	void testConfigMapMountPollingReload() {
		TestUtil.reCreateSources(util, client);
		TestUtil.patchFive(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);
		ConfigMapMountPollingReloadDelegate.testConfigMapMountPollingReload(client, util, K3S, IMAGE_NAME);
	}

	void testPollingReloadConfigMapWithBootstrap() {
		TestUtil.reCreateSources(util, client);
		TestUtil.patchSix(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);
		BootstrapEnabledPollingReloadConfigMapMountDelegate.testPollingReloadConfigMapWithBootstrap(client, util, K3S,
				IMAGE_NAME);
	}

	private static void manifests(Phase phase) {

		InputStream deploymentStream = util.inputStream("deployment.yaml");
		InputStream serviceStream = util.inputStream("service.yaml");
		InputStream ingressStream = util.inputStream("ingress.yaml");
		InputStream leftConfigMapStream = util.inputStream("left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("right-configmap.yaml");
		InputStream rightWithLabelConfigMapStream = util.inputStream("right-configmap-with-label.yaml");
		InputStream configMapAsStream = util.inputStream("configmap.yaml");
		InputStream secretAsStream = util.inputStream("secret.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);

		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);
		ConfigMap leftConfigMap = Serialization.unmarshal(leftConfigMapStream, ConfigMap.class);
		ConfigMap rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);
		ConfigMap rightWithLabelConfigMap = Serialization.unmarshal(rightWithLabelConfigMapStream, ConfigMap.class);
		ConfigMap configMap = Serialization.unmarshal(configMapAsStream, ConfigMap.class);
		Secret secret = Serialization.unmarshal(secretAsStream, Secret.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait("left", leftConfigMap, null);
			util.createAndWait("right", rightConfigMap, null);
			util.createAndWait("right", rightWithLabelConfigMap, null);
			util.createAndWait(NAMESPACE, configMap, secret);
			util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait("left", leftConfigMap, null);
			util.deleteAndWait("right", rightConfigMap, null);
			util.deleteAndWait("right", rightWithLabelConfigMap, null);
			util.deleteAndWait(NAMESPACE, configMap, secret);
			util.deleteAndWait(NAMESPACE, deployment, service, ingress);
		}

	}

}
