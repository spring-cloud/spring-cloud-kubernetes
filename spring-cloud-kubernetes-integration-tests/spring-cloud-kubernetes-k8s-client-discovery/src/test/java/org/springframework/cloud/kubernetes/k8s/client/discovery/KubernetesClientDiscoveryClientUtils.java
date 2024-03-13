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

import java.util.Map;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithMerge;
import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author wind57
 */
final class KubernetesClientDiscoveryClientUtils {

	private static final Map<String, String> POD_LABELS = Map.of("app", "spring-cloud-kubernetes-k8s-client-discovery");

	// patch the filter so that it matches both namespaces
	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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

	// patch to include all namespaces + external name services
	private static final String BODY_FIVE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
								"env": [
									{
										"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES",
										"value": "TRUE"
									},
									{
										"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_INCLUDEEXTERNALNAMESERVICES",
										"value": "TRUE"
									}
								]
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
								"name": "spring-cloud-kubernetes-k8s-client-discovery",
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
		patchWithMerge(deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

	static void patchForReactiveHealth(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_TWO, POD_LABELS);
	}

	static void patchForBlockingAndReactiveHealth(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_THREE, POD_LABELS);
	}

	// notice the usage of 'PATCH_FORMAT_JSON_MERGE_PATCH' here, it will not merge
	// env variables
	static void patchForBlockingHealth(String image, String deploymentName, String namespace) {
		patchWithReplace(image, deploymentName, namespace, BODY_FOUR, POD_LABELS);
	}

	// add SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES=TRUE
	// and SPRING_CLOUD_KUBERNETES_DISCOVERY_INCLUDEEXTERNALNAMESERVICES=TRUE
	static void patchForAllNamespaces(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_FIVE, POD_LABELS);
	}

	static void patchForSingleNamespace(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_SIX, POD_LABELS);
	}

	static void patchForPodMetadata(String imageName, String deploymentName, String namespace) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_SEVEN, POD_LABELS);
	}

	static void patchForReactiveOnly(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_EIGHT, POD_LABELS);
	}

	static void patchForBlockingAndReactive(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_NINE, POD_LABELS);
	}

	static void patchForTwoNamespacesBlockingOnly(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_TEN, POD_LABELS);
	}

	static void patchToAddBlockingSupport(String deploymentName, String namespace) {
		patchWithMerge(deploymentName, namespace, BODY_ELEVEN, POD_LABELS);
	}

	static void patchForUATNamespacesTests(String image, String deploymentName, String namespace) {
		patchWithReplace(image, deploymentName, namespace, BODY_TWELVE, POD_LABELS);
	}

}
