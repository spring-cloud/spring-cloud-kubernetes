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
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Fabric8Utils;
import org.springframework.cloud.kubernetes.integration.tests.commons.K8SUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
class CatalogWatchIT {

	private static final String APP_NAME = "spring-cloud-kubernetes-fabric8-client-catalog-watcher";

	private static final String NAMESPACE = "default";

	private static final K3sContainer K3S = Commons.container();

	private static KubernetesClient client;

	private static String busyboxServiceName;

	private static String busyboxDeploymentName;

	private static String appDeploymentName;

	private static String appServiceName;

	private static String appIngressName;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Config config = Config.fromKubeconfig(K3S.getKubeConfigYaml());
		client = new DefaultKubernetesClient(config);

		Commons.validateImage(APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(APP_NAME, K3S);

		Fabric8Utils.setUp(client, "default");
	}

	@BeforeEach
	void beforeEach() throws Exception {
		deployBusyboxManifests();
	}

	@AfterEach
	void afterEach() {
		deleteApp();
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
	void testCatalogWatchWithEndpoints() {
		deployApp(false);
		test();
	}

	@Test
	void testCatalogWatchWithEndpointSlices() {
		deployApp(true);
		test();
	}

	/**
	 * the test is the same for both endpoints and endpoint slices, the set-up for them is
	 * different.
	 */
	@SuppressWarnings("unchecked")
	private void test() {

		WebClient client = builder().baseUrl("localhost/result").build();
		EndpointNameAndNamespace[] holder = new EndpointNameAndNamespace[2];
		ResolvableType resolvableType = ResolvableType.forClassWithGenerics(List.class, EndpointNameAndNamespace.class);

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			// we get 3 pods as input, but because they are sorted by name in the catalog
			// watcher implementation
			// we will get the first busybox instances here.
			if (result != null) {
				holder[0] = result.get(0);
				holder[1] = result.get(1);
				return true;
			}

			return false;
		});

		EndpointNameAndNamespace resultOne = holder[0];
		EndpointNameAndNamespace resultTwo = holder[1];

		Assertions.assertNotNull(resultOne);
		Assertions.assertNotNull(resultTwo);

		Assertions.assertTrue(resultOne.endpointName().contains("busybox"));
		Assertions.assertTrue(resultTwo.endpointName().contains("busybox"));
		Assertions.assertEquals("default", resultOne.namespace());
		Assertions.assertEquals("default", resultTwo.namespace());

		deleteBusyboxApp();

		// what we get after delete
		EndpointNameAndNamespace[] afterDelete = new EndpointNameAndNamespace[1];

		await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(240)).until(() -> {
			List<EndpointNameAndNamespace> result = (List<EndpointNameAndNamespace>) client.method(HttpMethod.GET)
					.retrieve().bodyToMono(ParameterizedTypeReference.forType(resolvableType.getType()))
					.retryWhen(retrySpec()).block();

			// we need to get the event from KubernetesCatalogWatch, but that happens
			// on periodic bases. So in order to be sure we got the event we care about
			// we wait until the result has a single entry, which means busybox was
			// deleted
			// + KubernetesCatalogWatch received the new update.
			if (result != null && result.size() != 1) {
				return false;
			}

			// we will only receive one pod here, our own
			if (result != null) {
				afterDelete[0] = result.get(0);
				return true;
			}

			return false;
		});

		Assertions.assertTrue(afterDelete[0].endpointName().contains(APP_NAME));
		Assertions.assertEquals("default", afterDelete[0].namespace());

	}

	private void deployBusyboxManifests() throws Exception {

		Deployment deployment = client.apps().deployments().load(getBusyboxDeployment()).get();

		String[] image = K8SUtils.getImageFromDeployment(deployment).split(":");
		Commons.pullImage(image[0], image[1], K3S);
		Commons.loadImage(image[0], image[1], "busybox", K3S);

		client.apps().deployments().inNamespace(NAMESPACE).create(deployment);
		busyboxDeploymentName = deployment.getMetadata().getName();

		Service busyboxService = client.services().load(getBusyboxService()).get();
		busyboxServiceName = busyboxService.getMetadata().getName();
		client.services().inNamespace(NAMESPACE).create(busyboxService);

		Fabric8Utils.waitForDeployment(client, busyboxDeploymentName, NAMESPACE, 2, 600);

	}

	private static void deployApp(boolean useEndpointSlices) {

		InputStream deployment = useEndpointSlices ? getEndpointSlicesAppDeployment() : getEndpointsAppDeployment();
		Deployment appDeployment = client.apps().deployments().load(deployment).get();

		String version = K8SUtils.getPomVersion();
		String currentImage = appDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
		appDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(currentImage + ":" + version);

		client.apps().deployments().inNamespace(NAMESPACE).create(appDeployment);
		appDeploymentName = appDeployment.getMetadata().getName();

		Service appService = client.services().load(getAppService()).get();
		appServiceName = appService.getMetadata().getName();
		client.services().inNamespace(NAMESPACE).create(appService);

		Fabric8Utils.waitForDeployment(client, appDeploymentName, NAMESPACE, 2, 600);

		Ingress appIngress = client.network().v1().ingresses().load(getAppIngress()).get();
		appIngressName = appIngress.getMetadata().getName();
		client.network().v1().ingresses().inNamespace(NAMESPACE).create(appIngress);

		Fabric8Utils.waitForIngress(client, appIngressName, NAMESPACE);

	}

	private void deleteBusyboxApp() {
		Fabric8Utils.deleteDeployment(client, NAMESPACE, busyboxDeploymentName);
		Fabric8Utils.deleteService(client, NAMESPACE, busyboxServiceName);
	}

	private void deleteApp() {
		Fabric8Utils.deleteDeployment(client, NAMESPACE, appDeploymentName);
		Fabric8Utils.deleteService(client, NAMESPACE, appServiceName);
		Fabric8Utils.deleteIngress(client, NAMESPACE, appIngressName);
	}

	private static InputStream getBusyboxService() {
		return Fabric8Utils.inputStream("busybox/service.yaml");
	}

	private static InputStream getBusyboxDeployment() {
		return Fabric8Utils.inputStream("busybox/deployment.yaml");
	}

	/**
	 * deployment where support for endpoint slices is equal to false
	 */
	private static InputStream getEndpointsAppDeployment() {
		return Fabric8Utils.inputStream("app/watcher-endpoints-deployment.yaml");
	}

	private static InputStream getEndpointSlicesAppDeployment() {
		return Fabric8Utils.inputStream("app/watcher-endpoint-slices-deployment.yaml");
	}

	private static InputStream getAppIngress() {
		return Fabric8Utils.inputStream("app/watcher-ingress.yaml");
	}

	private static InputStream getAppService() {
		return Fabric8Utils.inputStream("app/watcher-service.yaml");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
