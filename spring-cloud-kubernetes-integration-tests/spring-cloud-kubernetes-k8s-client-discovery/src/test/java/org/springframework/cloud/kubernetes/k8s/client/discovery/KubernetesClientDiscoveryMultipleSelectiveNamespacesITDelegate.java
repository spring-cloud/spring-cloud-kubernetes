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

package org.springframework.cloud.kubernetes.k8s.client.discovery;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
class KubernetesClientDiscoveryMultipleSelectiveNamespacesITDelegate {

	private static final String BLOCKING_PUBLISH = "Will publish InstanceRegisteredEvent from blocking implementation";

	private static final String REACTIVE_PUBLISH = "Will publish InstanceRegisteredEvent from reactive implementation";

	private static final String IMAGE_NAME = "spring-cloud-kubernetes-k8s-client-discovery";

	/**
	 * Deploy wiremock in 3 namespaces: default, a, b. Search in selective namespaces 'a'
	 * and 'b' with blocking enabled and reactive disabled, as such find services and it's
	 * instances.
	 */
	void testTwoNamespacesBlockingOnly(K3sContainer container) {

		Commons.waitForLogStatement("using selective namespaces : [a, b]", container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesPresent : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : a", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : b", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : a", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : b", container, IMAGE_NAME);

		// this tiny checks makes sure that blocking is enabled and reactive is disabled.
		Commons.waitForLogStatement(BLOCKING_PUBLISH, container, IMAGE_NAME);
		Assertions.assertFalse(logs(container).contains(REACTIVE_PUBLISH));

		blockingCheck();

	}

	/**
	 * Deploy wiremock in 3 namespaces: default, a, b. Search in selective namespaces 'a'
	 * and 'b' with blocking disabled and reactive enabled, as such find services and it's
	 * instances.
	 */
	void testTwoNamespaceReactiveOnly(K3sContainer container) {

		Commons.waitForLogStatement("using selective namespaces : [a, b]", container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesPresent : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : a", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : b", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : a", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : b", container, IMAGE_NAME);

		// this tiny checks makes sure that blocking is disabled and reactive is enabled.
		Commons.waitForLogStatement(REACTIVE_PUBLISH, container, IMAGE_NAME);
		Assertions.assertFalse(logs(container).contains(BLOCKING_PUBLISH));

		reactiveCheck();

	}

	/**
	 * Deploy wiremock in 3 namespaces: default, a, b. Search in selective namespaces 'a'
	 * and 'b' with blocking enabled and reactive enabled, as such find services and its
	 * service instances.
	 */
	void testTwoNamespacesBothBlockingAndReactive(K3sContainer container) {

		Commons.waitForLogStatement("using selective namespaces : [a, b]", container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesMissing : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("ConditionalOnSelectiveNamespacesPresent : found selective namespaces : [a, b]",
				container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : a", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for services) in namespace : b", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : a", container, IMAGE_NAME);
		Commons.waitForLogStatement("registering lister (for endpoints) in namespace : b", container, IMAGE_NAME);

		// this tiny checks makes sure that blocking is enabled and reactive is enabled.
		Commons.waitForLogStatement(BLOCKING_PUBLISH, container, IMAGE_NAME);
		Assertions.assertTrue(logs(container).contains(REACTIVE_PUBLISH));

		blockingCheck();
		reactiveCheck();

	}

	private String logs(K3sContainer container) {
		try {
			String appPodName = container.execInContainer("sh", "-c",
					"kubectl get pods -l app=" + IMAGE_NAME + " -o=name --no-headers | tr -d '\n'").getStdout();

			Container.ExecResult execResult = container.execInContainer("sh", "-c",
					"kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private void reactiveCheck() {
		WebClient servicesClient = builder().baseUrl("http://localhost/reactive/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		// we get two here, but since there is 'distinct' call, only 1 will be reported
		// but service instances will report 2 nevertheless
		Assertions.assertEquals(servicesResult.size(), 1);
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		WebClient ourServiceClient = builder().baseUrl("http://localhost/reactive/service-instances/service-wiremock")
				.build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 2);
		ourServiceInstances = ourServiceInstances.stream()
				.sorted(Comparator.comparing(DefaultKubernetesServiceInstance::namespace)).toList();

		DefaultKubernetesServiceInstance serviceInstanceA = ourServiceInstances.get(0);
		// we only care about namespace here, as all other fields are tested in various
		// other tests.
		Assertions.assertEquals(serviceInstanceA.getNamespace(), "a");

		DefaultKubernetesServiceInstance serviceInstanceB = ourServiceInstances.get(1);
		// we only care about namespace here, as all other fields are tested in various
		// other tests.
		Assertions.assertEquals(serviceInstanceB.getNamespace(), "b");
	}

	private void blockingCheck() {
		WebClient servicesClient = builder().baseUrl("http://localhost/services").build();

		List<String> servicesResult = servicesClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<String>>() {

				}).retryWhen(retrySpec()).block();

		// we get two here, but since there is 'distinct' call, only 1 will be reported
		// but service instances will report 2 nevertheless
		Assertions.assertEquals(servicesResult.size(), 1);
		Assertions.assertTrue(servicesResult.contains("service-wiremock"));

		WebClient ourServiceClient = builder().baseUrl("http://localhost/service-instances/service-wiremock").build();

		List<DefaultKubernetesServiceInstance> ourServiceInstances = ourServiceClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(new ParameterizedTypeReference<List<DefaultKubernetesServiceInstance>>() {

				}).retryWhen(retrySpec()).block();

		Assertions.assertEquals(ourServiceInstances.size(), 2);
		ourServiceInstances = ourServiceInstances.stream()
				.sorted(Comparator.comparing(DefaultKubernetesServiceInstance::namespace)).toList();

		DefaultKubernetesServiceInstance serviceInstanceA = ourServiceInstances.get(0);
		// we only care about namespace here, as all other fields are tested in various
		// other tests.
		Assertions.assertEquals(serviceInstanceA.getNamespace(), "a");

		DefaultKubernetesServiceInstance serviceInstanceB = ourServiceInstances.get(1);
		// we only care about namespace here, as all other fields are tested in various
		// other tests.
		Assertions.assertEquals(serviceInstanceB.getNamespace(), "b");
	}

	private WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	private RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
