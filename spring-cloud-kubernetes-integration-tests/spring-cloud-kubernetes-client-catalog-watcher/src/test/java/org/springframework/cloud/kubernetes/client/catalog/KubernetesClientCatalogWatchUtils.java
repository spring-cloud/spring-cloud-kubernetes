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

package org.springframework.cloud.kubernetes.client.catalog;

import java.util.Map;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithMerge;
import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author wind57
 */
final class KubernetesClientCatalogWatchUtils {

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-catalog-watcher",
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

	private KubernetesClientCatalogWatchUtils() {

	}
	
	static void patchForEndpointSlices(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_ONE);
	}

}
