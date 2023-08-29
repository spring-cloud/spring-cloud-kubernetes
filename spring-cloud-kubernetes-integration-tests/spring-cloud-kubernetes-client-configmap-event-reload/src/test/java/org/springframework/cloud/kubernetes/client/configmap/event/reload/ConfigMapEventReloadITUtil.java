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

package org.springframework.cloud.kubernetes.client.configmap.event.reload;

import java.util.Map;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author wind57
 */
final class ConfigMapEventReloadITUtil {

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-client-configmap-event-reload");

	private ConfigMapEventReloadITUtil() {

	}

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-configmap-event-reload",
								"image": "image_name_here",
								"livenessProbe": {
								"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"readinessProbe": {
									"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"env": [
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "two"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD",
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
								"name": "spring-cloud-kubernetes-client-configmap-event-reload",
								"image": "image_name_here",
								"livenessProbe": {
									"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"readinessProbe": {
									"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"env": [
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "three"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD",
									"value": "DEBUG"
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
								"name": "spring-cloud-kubernetes-client-configmap-event-reload",
								"image": "image_name_here",
								"livenessProbe": {
								"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"readinessProbe": {
									"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"env": [
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "two"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_SECRETS_ENABLED",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_FOUR = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-client-configmap-event-reload",
								"image": "image_name_here",
								"livenessProbe": {
								"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/liveness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"readinessProbe": {
									"failureThreshold": 3,
									"httpGet": {
										"path": "/actuator/health/readiness",
										"port": 8080,
										"scheme": "HTTP"
									},
									"periodSeconds": 10,
									"successThreshold": 1,
									"timeoutSeconds": 1
								},
								"env": [
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "one"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_CLIENT_CONFIG_RELOAD",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_SECRETS_ENABLED",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static void patchOne(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

	static void patchTwo(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_TWO, POD_LABELS);
	}

	static void patchThree(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_THREE, POD_LABELS);
	}

	static void patchFour(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_FOUR, POD_LABELS);
	}

}
