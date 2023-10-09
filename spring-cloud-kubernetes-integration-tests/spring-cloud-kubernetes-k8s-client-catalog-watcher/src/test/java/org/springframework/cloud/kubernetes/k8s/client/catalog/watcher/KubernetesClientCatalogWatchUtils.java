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

package org.springframework.cloud.kubernetes.k8s.client.catalog.watcher;

import java.util.Map;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author wind57
 */
final class KubernetesClientCatalogWatchUtils {

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-k8s-client-catalog-watcher");

	private KubernetesClientCatalogWatchUtils() {

	}

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-catalog-watcher",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_USE_ENDPOINT_SLICES",
									"value": "TRUE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY_CATALOG",
									"value": "DEBUG"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_TWO = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-catalog-watcher",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_USE_ENDPOINT_SLICES",
									"value": "FALSE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY_CATALOG",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "namespacea"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1",
									"value": "namespaceb"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_THREE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-catalog-watcher",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_USE_ENDPOINT_SLICES",
									"value": "TRUE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY_CATALOG",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "namespacea"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1",
									"value": "namespaceb"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static void patchForEndpointSlices(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

	static void patchForEndpointsNamespaces(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_TWO, POD_LABELS);
	}

	static void patchForEndpointSlicesNamespaces(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_THREE, POD_LABELS);
	}

}
