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

/**
 * @author wind57
 */
final class KubernetesClientDiscoveryClientUtils {

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

	private KubernetesClientDiscoveryClientUtils() {

	}

	static void patchForTwoNamespacesMatchViaThePredicate(String deploymentName, String namespace) {

		try {
			PatchUtils.patch(V1Deployment.class,
					() -> new AppsV1Api().patchNamespacedDeploymentCall(deploymentName, namespace,
							new V1Patch(BODY_ONE), null, null, null, null, null, null),
					V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH, new CoreV1Api().getApiClient());
		}
		catch (ApiException e) {
			throw new RuntimeException(e);
		}
	}

}
