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

package org.springframework.cloud.kubernetes.k8s.client.reload.configmap;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author wind57
 */
final class K8sClientConfigMapReloadITUtil {

	private static final Map<String, String> POD_LABELS = Map.of("app", "spring-k8s-client-reload");

	private K8sClientConfigMapReloadITUtil() {
	}

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-k8s-client-reload",
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
									"name": "SPRING_CLOUD_BOOTSTRAP_ENABLED",
									"value": "TRUE"
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
								"name": "spring-k8s-client-reload",
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
								},
								{
									"name": "SPRING_CLOUD_BOOTSTRAP_ENABLED",
									"value": "TRUE"
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
								"name": "spring-k8s-client-reload",
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
								},
								{
									"name": "SPRING_CLOUD_BOOTSTRAP_ENABLED",
									"value": "TRUE"
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
								"name": "spring-k8s-client-reload",
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
								},
								{
									"name": "SPRING_CLOUD_BOOTSTRAP_ENABLED",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_FIVE = """
			{
				"spec": {
					"template": {
						"spec": {
							"volumes": [
								{
									"configMap": {
										"defaultMode": 420,
										"name": "poll-reload-as-mount"
									},
									"name": "config-map-volume"
								}
							],
							"containers": [{
								"name": "spring-k8s-client-reload",
								"image": "image_name_here",
								"volumeMounts": [
									{
										"mountPath": "/tmp",
										"name": "config-map-volume"
									}
								],
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
									"value": "mount"
								},
								{
									"name": "SPRING_CLOUD_BOOTSTRAP_ENABLED",
									"value": "FALSE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG_RELOAD",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS",
									"value": "DEBUG"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private static final String BODY_SIX = """
			{
				"spec": {
					"template": {
						"spec": {
							"volumes": [
								{
									"configMap": {
										"defaultMode": 420,
										"name": "poll-reload-as-mount"
									},
									"name": "config-map-volume"
								}
							],
							"containers": [{
								"name": "spring-k8s-client-reload",
								"image": "image_name_here",
								"volumeMounts": [
									{
										"mountPath": "/tmp",
										"name": "config-map-volume"
									}
								],
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
									"value": "with-bootstrap"
								},
								{
									"name": "SPRING_CLOUD_BOOTSTRAP_ENABLED",
									"value": "TRUE"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG_RELOAD",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_CONFIG",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS",
									"value": "DEBUG"
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

	static void patchFive(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_FIVE, POD_LABELS);
	}

	static void patchSix(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_SIX, POD_LABELS);
	}

	static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(1)).filter(Objects::nonNull);
	}

	static String logs(String appLabelValue, K3sContainer k3sContainer) {
		try {
			String appPodName = k3sContainer
					.execInContainer("sh", "-c",
							"kubectl get pods -l app=" + appLabelValue + " -o=name --no-headers | tr -d '\n'")
					.getStdout();

			Container.ExecResult execResult = k3sContainer.execInContainer("sh", "-c",
					"kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}
