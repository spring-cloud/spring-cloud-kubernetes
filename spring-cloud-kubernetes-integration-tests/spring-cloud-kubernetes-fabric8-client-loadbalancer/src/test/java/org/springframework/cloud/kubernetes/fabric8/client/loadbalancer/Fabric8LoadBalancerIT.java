/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.loadbalancer;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.kubernetes.fabric8.loadbalancer.Fabric8ServicesListSupplier;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.Phase;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.integration.tests.commons.Commons.pomVersion;

/**
 * @author wind57
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Fabric8LoadBalancerIT {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(Fabric8LoadBalancerIT.class);

	private static final String WIREMOCK_URL = "http://localhost:80/loadbalancer/wiremock";

	private static final String HTTPD_URL = "http://localhost:80/loadbalancer/httpd";

	private static final String SUPPLIER_URL = "http://localhost:80/loadbalancer/supplier";

	private static final String SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME = "spring-cloud-kubernetes-fabric8-client-loadbalancer";

	private static final String DEFAULT_NAMESPACE = "default";

	private static final String NON_DEFAULT_NAMESPACE = "a";

	private static final K3sContainer K3S = Commons.container();

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-fabric8-client-loadbalancer";

	private static final String DOCKER_IMAGE = "docker.io/springcloud/" + IMAGE_NAME + ":" + pomVersion();

	private static Util util;

	@BeforeAll
	static void beforeAll() throws Exception {
		K3S.start();
		Commons.validateImage(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		Commons.loadSpringCloudKubernetesImage(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		util = new Util(K3S);
		util.createNamespace(NON_DEFAULT_NAMESPACE);
		util.setUp(DEFAULT_NAMESPACE);
		util.setUpClusterWideClusterRoleBinding(DEFAULT_NAMESPACE);
		util.wiremock(DEFAULT_NAMESPACE, "/wiremock", Phase.CREATE, false);
		util.httpd(NON_DEFAULT_NAMESPACE, Phase.CREATE);
		loadbalancerIt(Phase.CREATE);
	}

	@AfterAll
	static void afterAll() throws Exception {
		util.wiremock(DEFAULT_NAMESPACE, "/wiremock", Phase.DELETE, false);
		util.httpd(NON_DEFAULT_NAMESPACE, Phase.DELETE);
		loadbalancerIt(Phase.DELETE);
		Commons.cleanUp(SPRING_CLOUD_K8S_LOADBALANCER_APP_NAME, K3S);
		Commons.systemPrune();
	}

	/**
	 * <pre>
	 *      - wiremock is present in 'default' namespace
	 *      - httpd is present in 'non-default' namespace
	 *      - we enable search in all namespaces
	 *      - load balancer mode is 'SERVICE'
	 *
	 *      - as such, both services can be load balanced.
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the SERVICE mode.
	 * </pre>
	 */
	@Test
	@Order(1)
	void testLoadBalancerServiceModeAllNamespaces() {

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(WIREMOCK_URL).build();
		String wiremockResult = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(wiremockResult)).extractingJsonPathArrayValue("$.mappings")
				.isEmpty();
		Assertions.assertThat(BASIC_JSON_TESTER.from(wiremockResult)).extractingJsonPathNumberValue("$.meta.total")
				.isEqualTo(0);

		WebClient.Builder httpDBuilder = builder();
		WebClient httpdServiceClient = httpDBuilder.baseUrl(HTTPD_URL).build();
		String httpdResult = httpdServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(httpdResult).contains("It works");

		WebClient.Builder supplierBuilder = builder();
		WebClient supplierServiceClient = supplierBuilder.baseUrl(SUPPLIER_URL).build();
		String supplierResult = supplierServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(supplierResult).isEqualTo(Fabric8ServicesListSupplier.class.getSimpleName());

		String logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs).contains("serviceID : service-wiremock");
		Assertions.assertThat(logs).contains("serviceID : service-httpd");
		Assertions.assertThat(logs).contains("discovering services in all namespaces");

	}

	/**
	 * <pre>
	 *      - wiremock is present in 'default' namespace
	 *      - httpd is present in 'non-default' namespace
	 *      - we enable search in all namespaces
	 *      - load balancer mode is 'POD'
	 *
	 *      - as such, both services can be load balanced.
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the POD mode.
	 * </pre>
	 */
	@Test
	@Order(2)
	void testLoadBalancerPodModeAllNamespaces() {

		TestUtil.patchOne(util, DOCKER_IMAGE, IMAGE_NAME, DEFAULT_NAMESPACE);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(WIREMOCK_URL).build();
		String wiremockResult = serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(BASIC_JSON_TESTER.from(wiremockResult)).extractingJsonPathArrayValue("$.mappings")
				.isEmpty();
		Assertions.assertThat(BASIC_JSON_TESTER.from(wiremockResult)).extractingJsonPathNumberValue("$.meta.total")
				.isEqualTo(0);

		WebClient.Builder httpDBuilder = builder();
		WebClient httpdServiceClient = httpDBuilder.baseUrl(HTTPD_URL).build();
		String httpdResult = httpdServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(httpdResult).contains("It works");

		WebClient.Builder supplierBuilder = builder();
		WebClient supplierServiceClient = supplierBuilder.baseUrl(SUPPLIER_URL).build();
		String supplierResult = supplierServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(supplierResult)
				.isEqualTo(DiscoveryClientServiceInstanceListSupplier.class.getSimpleName());

		String logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs).doesNotContain("serviceID : service-wiremock");
		Assertions.assertThat(logs).doesNotContain("serviceID : service-httpd");
		Assertions.assertThat(logs).doesNotContain("discovering services in all namespaces");

	}

	/**
	 * <pre>
	 *      - wiremock is present in 'default' namespace
	 *      - httpd is present in 'non-default' namespace
	 *      - we enable search in namespace 'a'
	 *      - load balancer mode is 'SERVICE'
	 *
	 *      - as such, only httpd service is load balanced
	 *      - we assert that wiremock is not found via
	 *        'did not find service with name : service-wiremock in namespace : a' log
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the SERVICE mode.
	 * </pre>
	 */
	@Test
	@Order(3)
	void testLoadBalancerServiceModeSpecificNamespace() {

		TestUtil.patchTwo(util, DOCKER_IMAGE, IMAGE_NAME, DEFAULT_NAMESPACE);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(WIREMOCK_URL).build();
		boolean caught = false;
		try {
			serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		}
		catch (Exception e) {
			caught = true;
		}

		if (!caught) {
			Assertions.fail("should have failed");
		}

		String logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs).contains("did not find service with name : service-wiremock in namespace : a");

		WebClient.Builder httpDBuilder = builder();
		WebClient httpdServiceClient = httpDBuilder.baseUrl(HTTPD_URL).build();
		String httpdResult = httpdServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(httpdResult).contains("It works");

		WebClient.Builder supplierBuilder = builder();
		WebClient supplierServiceClient = supplierBuilder.baseUrl(SUPPLIER_URL).build();
		String supplierResult = supplierServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(supplierResult).isEqualTo(Fabric8ServicesListSupplier.class.getSimpleName());

		logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs).contains("serviceID : service-wiremock");
		Assertions.assertThat(logs).contains("discovering services in namespace : a");

	}

	/**
	 * <pre>
	 *      - wiremock is present in 'default' namespace
	 *      - httpd is present in 'non-default' namespace
	 *      - we enable search in namespace 'a'
	 *      - load balancer mode is 'POD'
	 *
	 *      - as such, only httpd service is load balanced
	 *      - we assert that wiremock is not found via
	 *        'did not find service with name : service-wiremock in namespace : a' log
	 *      - we also assert the type of ServiceInstanceListSupplier corresponding to the POD mode.
	 * </pre>
	 */
	@Test
	@Order(4)
	void testLoadBalancerPodModeSpecificNamespace() {

		TestUtil.patchThree(util, DOCKER_IMAGE, IMAGE_NAME, DEFAULT_NAMESPACE);

		WebClient.Builder builder = builder();
		WebClient serviceClient = builder.baseUrl(WIREMOCK_URL).build();
		boolean caught = false;
		try {
			serviceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec()).block();
		}
		catch (Exception e) {
			caught = true;
		}

		if (!caught) {
			Assertions.fail("should have failed");
		}

		String logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs)
				.doesNotContain("did not find service with name : service-wiremock in namespace : a");

		WebClient.Builder httpDBuilder = builder();
		WebClient httpdServiceClient = httpDBuilder.baseUrl(HTTPD_URL).build();
		String httpdResult = httpdServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(httpdResult).contains("It works");

		WebClient.Builder supplierBuilder = builder();
		WebClient supplierServiceClient = supplierBuilder.baseUrl(SUPPLIER_URL).build();
		String supplierResult = supplierServiceClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(retrySpec()).block();
		Assertions.assertThat(supplierResult)
				.isEqualTo(DiscoveryClientServiceInstanceListSupplier.class.getSimpleName());

		logs = util.logStatements(K3S, "spring-cloud-kubernetes-fabric8-client-loadbalancer");
		Assertions.assertThat(logs).doesNotContain("serviceID : service-wiremock");
		Assertions.assertThat(logs).doesNotContain("discovering services in namespace : a");

	}

	private static void loadbalancerIt(Phase phase) {

		InputStream deploymentStream = util
				.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-deployment.yaml");
		InputStream serviceStream = util
				.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-service.yaml");
		InputStream ingressStream = util
				.inputStream("spring-cloud-kubernetes-fabric8-client-loadbalancer-ingress.yaml");

		Deployment deployment = Serialization.unmarshal(deploymentStream, Deployment.class);
		Service service = Serialization.unmarshal(serviceStream, Service.class);
		Ingress ingress = Serialization.unmarshal(ingressStream, Ingress.class);

		if (phase.equals(Phase.CREATE)) {
			util.createAndWait(DEFAULT_NAMESPACE, null, deployment, service, ingress, true);
		}
		else if (phase.equals(Phase.DELETE)) {
			util.deleteAndWait(DEFAULT_NAMESPACE, deployment, service, ingress);
		}
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(10, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

}
