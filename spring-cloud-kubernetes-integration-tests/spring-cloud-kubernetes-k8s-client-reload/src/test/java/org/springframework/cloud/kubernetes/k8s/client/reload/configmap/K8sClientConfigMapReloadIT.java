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

package org.springframework.cloud.kubernetes.k8s.client.reload.configmap;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.k8s.client.reload.configmap.BootstrapEnabledPollingReloadConfigMapMountDelegate.testBootstrapEnabledPollingReloadConfigMapMount;
import static org.springframework.cloud.kubernetes.k8s.client.reload.configmap.DataChangesInConfigMapReloadDelegate.testSimple;
import static org.springframework.cloud.kubernetes.k8s.client.reload.configmap.K8sClientConfigMapReloadITUtil.builder;
import static org.springframework.cloud.kubernetes.k8s.client.reload.configmap.K8sClientConfigMapReloadITUtil.patchThree;
import static org.springframework.cloud.kubernetes.k8s.client.reload.configmap.K8sClientConfigMapReloadITUtil.retrySpec;
import static org.springframework.cloud.kubernetes.k8s.client.reload.configmap.PollingReloadConfigMapMountDelegate.testPollingReloadConfigMapMount;

/**
 * @author wind57
 */
class K8sClientConfigMapReloadIT {

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-reload";

	private static final String DEPLOYMENT_NAME = "spring-k8s-client-reload";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + Commons.pomVersion();

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	private static CoreV1Api api;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);
		util = new Util(K3S);
		util.createNamespace("left");
		util.createNamespace("right");
		util.setUpClusterWide(NAMESPACE, Set.of("left", "right"));
		util.setUp(NAMESPACE);
		api = new CoreV1Api();
	}

	@AfterAll
	static void afterAll() {
		util.deleteClusterWide(NAMESPACE, Set.of("left", "right"));
		manifests(Phase.DELETE);
		util.deleteNamespace("left");
		util.deleteNamespace("right");
	}

	// since we patch each deployment with "replace" strategy, any of the above can be
	// commented out and debugged individually.
	private void testAllOther() throws Exception {
		testSimple(DOCKER_IMAGE, DEPLOYMENT_NAME, K3S);
		testPollingReloadConfigMapMount(DEPLOYMENT_NAME, K3S, util, DOCKER_IMAGE);
		testBootstrapEnabledPollingReloadConfigMapMount(DEPLOYMENT_NAME, K3S, util, DOCKER_IMAGE);

	}

	private static void manifests(Phase phase) {

		try {

			V1ConfigMap leftConfigMap = (V1ConfigMap) util.yaml("left-configmap.yaml");
			V1ConfigMap rightConfigMap = (V1ConfigMap) util.yaml("right-configmap.yaml");
			V1ConfigMap mountConfigMap = (V1ConfigMap) util.yaml("configmap-mount.yaml");

			V1Deployment deployment = (V1Deployment) util.yaml("deployment.yaml");
			V1Service service = (V1Service) util.yaml("service.yaml");
			V1Ingress ingress = (V1Ingress) util.yaml("ingress.yaml");

			if (phase.equals(Phase.CREATE)) {
				util.createAndWait(NAMESPACE, mountConfigMap, null);
				util.createAndWait("left", leftConfigMap, null);
				util.createAndWait("right", rightConfigMap, null);
				util.createAndWait(NAMESPACE, null, deployment, service, ingress, true);
			}

			if (phase.equals(Phase.DELETE)) {
				util.deleteAndWait(NAMESPACE, mountConfigMap, null);
				util.deleteAndWait("left", leftConfigMap, null);
				util.deleteAndWait("right", rightConfigMap, null);
				util.deleteAndWait(NAMESPACE, deployment, service, ingress);
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static void replaceConfigMap(V1ConfigMap configMap, String name) throws ApiException {
		api.replaceNamespacedConfigMap(name, "right", configMap, null, null, null, null);
	}

}
