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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Images;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.patchForEndpointSlices;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.patchForNamespaceFilterAndEndpointSlices;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.patchForNamespaceFilterAndEndpoints;
import static org.springframework.cloud.kubernetes.fabric8.catalog.watch.Fabric8CatalogWatchUtil.retrySpec;
import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;

/**
 * @author wind57
 */
class Fabric8CatalogWatchIT {

	private static final String NAMESPACE = "default";

	public static final String NAMESPACE_A = "namespacea";

	public static final String NAMESPACE_B = "namespaceb";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-catalog-watcher";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + pomVersion();

	private static final K3sContainer K3S = Commons.container();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(IMAGE_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(IMAGE_NAME, K3S);

		Images.loadBusybox(K3S);

		util = new Util(K3S);

		util.createNamespace(NAMESPACE_A);
		util.createNamespace(NAMESPACE_B);

		util.setUp(NAMESPACE);
		util.setUpClusterWide(NAMESPACE, Set.of(NAMESPACE, NAMESPACE_A, NAMESPACE_B));
		util.busybox(NAMESPACE, Phase.CREATE);

		app(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() {

		util.deleteNamespace(NAMESPACE_A);
		util.deleteNamespace(NAMESPACE_B);

		app(Phase.DELETE);
		Commons.systemPrune();
	}

	/**
	 * <pre>
	 *     - we deploy a busybox service with 2 replica pods
	 *     - we receive an event from KubernetesCatalogWatcher, assert what is inside it
	 *     - delete the busybox service
	 *     - assert that we receive only spring-cloud-kubernetes-fabric8-client-catalog-watcher pod
	 * </pre>
	 */
	@Test
	void testCatalogWatchWithEndpoints() throws Exception {
		//test();

//		testCatalogWatchWithEndpointSlices();
//		testCatalogWatchWithNamespaceFilterAndEndpoints();
//		testCatalogWatchWithNamespaceFilterAndEndpointSlices();
	}

	void testCatalogWatchWithEndpointSlices() {
		util.busybox(NAMESPACE, Phase.CREATE);
		patchForEndpointSlices(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);
		Commons.waitForLogStatement("stateGenerator is of type: Fabric8EndpointSliceV1CatalogWatch", K3S, IMAGE_NAME);
		//test();
	}

	void testCatalogWatchWithNamespaceFilterAndEndpoints() {
		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);
		patchForNamespaceFilterAndEndpoints(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);
		Fabric8CatalogWatchWithNamespacesDelegate.testCatalogWatchWithNamespaceFilterAndEndpoints(K3S, IMAGE_NAME,
				util);
	}

	void testCatalogWatchWithNamespaceFilterAndEndpointSlices() {
		util.busybox(NAMESPACE_A, Phase.CREATE);
		util.busybox(NAMESPACE_B, Phase.CREATE);
		patchForNamespaceFilterAndEndpointSlices(util, DOCKER_IMAGE, IMAGE_NAME, NAMESPACE);
		Fabric8CatalogWatchWithNamespacesDelegate.testCatalogWatchWithNamespaceFilterAndEndpointSlices(K3S, IMAGE_NAME,
				util);
	}

	private static void app(Phase phase) {

		InputStream endpointsDeploymentStream = util.inputStream("app/watcher-endpoints-deployment.yaml");
		InputStream serviceStream = util.inputStream("app/watcher-service.yaml");
		InputStream ingressStream = util.inputStream("app/watcher-ingress.yaml");

		Deployment deployment = Serialization.unmarshal(endpointsDeploymentStream, Deployment.class);
		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(Fabric8CatalogWatchIT.NAMESPACE, null, deployment, service, ingress, true);
		}
		else {
			util.deleteAndWait(Fabric8CatalogWatchIT.NAMESPACE, deployment, service, ingress);
		}

	}

}
