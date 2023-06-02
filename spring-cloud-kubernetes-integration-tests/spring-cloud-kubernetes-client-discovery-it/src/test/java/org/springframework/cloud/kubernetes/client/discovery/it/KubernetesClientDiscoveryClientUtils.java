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

package org.springframework.cloud.kubernetes.client.discovery.it;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.PatchUtils;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
final class KubernetesClientDiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(KubernetesClientDiscoveryClientUtils.class));

	// patch the filter so that it matches both namespaces
	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_FILTER",
									"value": "#root.metadata.namespace matches '^.*uat$'"
								}]
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
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH_REACTIVE",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY_REACTIVE",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED",
									"value": "FALSE"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	// this one patches on top of BODY_TWO, so it essentially enables both blocking and
	// reactive implementations
	// and adds proper packages in DEBUG mode, so that we could assert logs.
	private static final String BODY_THREE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [{
									"name": "SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED",
									"value": "TRUE"
								}]
							}]
						}
					}
				}
			}
						""";

	// this one patches on top of BODY_TWO, so it essentially enables both blocking and
	// reactive implementations
	// and adds proper packages in DEBUG mode, so that we could assert logs.
	private static final String BODY_FOUR = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED",
									"value": "FALSE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY",
									"value": "DEBUG"
								}
								]
							}]
						}
					}
				}
			}
						""";

	// patch to include all namespaces
	private static final String BODY_FIVE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES",
									"value": "TRUE"
								}]
							}]
						}
					}
				}
			}
						""";

	// disable all namespaces and include a single namespace to be discoverable
	private static final String BODY_SIX = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "a"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_SEVEN = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDLABELS",
									"value": "TRUE"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_LABELSPREFIX",
									"value": "label-"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ADDANNOTATIONS",
									"value": "TRUE"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_METADATA_ANNOTATIONSPREFIX",
									"value": "annotation-"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_EIGHT = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [
								{
									"name": "SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED",
									"value": "TRUE"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_NINE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [
								{
									"name": "SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED",
									"value": "TRUE"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_TEN = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1",
									"value": "b"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_ELEVEN = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"env": [
								{
									"name": "SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_TWELVE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_0",
									"value": "a-uat"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_NAMESPACES_1",
									"value": "b-uat"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_FILTER",
									"value": "#root.metadata.namespace matches 'a-uat$'"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_DISCOVERY",
									"value": "DEBUG"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private KubernetesClientDiscoveryClientUtils() {

	}

	static void patchForTwoNamespacesMatchViaThePredicate(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_ONE);
	}

	static void patchForReactiveHealth(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_TWO);
	}

	static void patchForBlockingAndReactiveHealth(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_THREE);
	}

	// notice the usage of 'PATCH_FORMAT_JSON_MERGE_PATCH' here, it will not merge
	// env variables
	static void patchForBlockingHealth(String image, String deploymentName, String namespace) {
		patchWithReplace(image, deploymentName, namespace, BODY_FOUR);
	}

	// add SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES=TRUE
	static void patchForAllNamespaces(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_FIVE);
	}

	static void patchForSingleNamespace(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_SIX);
	}

	static void patchForPodMetadata(String imageName, String deploymentName, String namespace) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_SEVEN);
	}

	static void patchForReactiveOnly(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_EIGHT);
	}

	static void patchForBlockingAndReactive(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_NINE);
	}

	static void patchForTwoNamespacesBlockingOnly(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_TEN);
	}

	static void patchToAddBlockingSupport(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_ELEVEN);
	}

	static void patchForUATNamespacesTests(String image, String deploymentName, String namespace) {
		patchWithReplace(image, deploymentName, namespace, BODY_TWELVE);
	}

	private static void patchWithMerge(String deploymentName, String namespace, String patchBody) {
		try {
			PatchUtils.patch(V1Deployment.class,
					() -> new AppsV1Api().patchNamespacedDeploymentCall(deploymentName, namespace,
							new V1Patch(patchBody), null, null, null, null, null, null),
					V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH, new CoreV1Api().getApiClient());
		}
		catch (ApiException e) {
			LOG.error(() -> "error : " + e.getResponseBody());
			throw new RuntimeException(e);
		}
	}

	private static void patchWithReplace(String imageName, String deploymentName, String namespace, String patchBody) {
		String body = patchBody.replace("image_name_here", imageName);

		try {
			PatchUtils.patch(V1Deployment.class,
					() -> new AppsV1Api().patchNamespacedDeploymentCall(deploymentName, namespace, new V1Patch(body),
							null, null, null, null, null, null),
					V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH, new CoreV1Api().getApiClient());
		}
		catch (ApiException e) {
			LOG.error(() -> "error : " + e.getResponseBody());
			throw new RuntimeException(e);
		}
	}

}
