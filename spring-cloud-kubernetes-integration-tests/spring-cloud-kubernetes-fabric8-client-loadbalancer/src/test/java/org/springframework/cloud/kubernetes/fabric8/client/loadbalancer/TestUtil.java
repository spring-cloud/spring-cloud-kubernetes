/*
 * Copyright 2013-2024 the original author or authors.
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

import java.util.Map;

import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;

/**
 * @author wind57
 */
final class TestUtil {

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-fabric8-client-loadbalancer");

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-loadbalancer",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_LOADBALANCER_MODE",
									"value": "POD"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_LOADBALANCER",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALLNAMESPACES",
									"value": "TRUE"
								}
								],
								"readinessProbe": {
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									}
								},
								"livenessProbe": {
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									}
								}
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
								"name": "spring-cloud-kubernetes-fabric8-client-loadbalancer",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_LOADBALANCER_MODE",
									"value": "SERVICE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_LOADBALANCER",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALLNAMESPACES",
									"value": "FALSE"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE",
									"value": "a"
								}
								],
								"readinessProbe": {
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									}
								},
								"livenessProbe": {
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									}
								}
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
								"name": "spring-cloud-kubernetes-fabric8-client-loadbalancer",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_CLOUD_KUBERNETES_LOADBALANCER_MODE",
									"value": "POD"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_LOADBALANCER",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_ALLNAMESPACES",
									"value": "FALSE"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE",
									"value": "a"
								}
								],
								"readinessProbe": {
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									}
								},
								"livenessProbe": {
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									}
								}
							}]
						}
					}
				}
			}
						""";

	private TestUtil() {

	}

	static void patchOne(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

	static void patchTwo(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_TWO, POD_LABELS);
	}

	static void patchThree(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_THREE, POD_LABELS);
	}

}