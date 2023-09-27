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

package org.springframework.cloud.kubernetes.fabric8.catalog.watch;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
final class Fabric8CatalogWatchUtil {

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-fabric8-client-catalog-watcher");

	private Fabric8CatalogWatchUtil() {

	}

	static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-catalog-watcher",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_USE_ENDPOINT_SLICES",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static final String BODY_TWO = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-catalog-watcher",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_USE_ENDPOINT_SLICES",
									"value": "FALSE"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "namespacea"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1",
									"value": "default"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static final String BODY_THREE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-catalog-watcher",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_USE_ENDPOINT_SLICES",
									"value": "TRUE"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "namespacea"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1",
									"value": "default"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static void patchForEndpointSlices(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

	static void patchForNamespaceFilterAndEndpoints(Util util, String dockerImage, String deploymentName,
			String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_TWO, POD_LABELS);
	}

	static void patchForNamespaceFilterAndEndpointSlices(Util util, String dockerImage, String deploymentName,
			String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_THREE, POD_LABELS);
	}

	static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(15, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

}
