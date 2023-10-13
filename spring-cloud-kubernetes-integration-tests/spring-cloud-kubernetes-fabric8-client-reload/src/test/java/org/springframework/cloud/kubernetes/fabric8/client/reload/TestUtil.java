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

package org.springframework.cloud.kubernetes.fabric8.client.reload;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.testcontainers.containers.Container;
import org.testcontainers.k3s.K3sContainer;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author wind57
 */
final class TestUtil {

	private static final Map<String, String> POD_LABELS = Map.of("app",
			"spring-cloud-kubernetes-fabric8-client-reload");

	private static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "two"
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
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "three"
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
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "two"
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
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_CONFIG_RELOAD",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "one"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_SECRETS_ENABLED",
									"value": "FALSE"
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

	private static final String BODY_FIVE = """
			{
				"spec": {
					"template": {
						"spec": {
							"volumes": [
								{
									"configMap": {
										"defaultMode": 420,
										"name": "poll-reload"
									},
									"name": "config-map-volume"
								}
							],
							"containers": [{
								"volumeMounts": [
									{
										"mountPath": "/tmp",
										"name": "config-map-volume"
									}
								],
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
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
										"name": "poll-reload"
									},
									"name": "config-map-volume"
								}
							],
							"containers": [{
								"volumeMounts": [
									{
										"mountPath": "/tmp",
										"name": "config-map-volume"
									}
								],
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
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

	private static final String BODY_SEVEN = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-configmap-event-reload",
								"image": "image_name_here",
								"env": [
								{
									"name": "SPRING_PROFILES_ACTIVE",
									"value": "with-secret"
								}
								]
							}]
						}
					}
				}
			}
						""";

	private TestUtil() {

	}

	static void reCreateSources(Util util, KubernetesClient client) {
		InputStream leftConfigMapStream = util.inputStream("left-configmap.yaml");
		InputStream rightConfigMapStream = util.inputStream("right-configmap.yaml");
		InputStream configMapStream = util.inputStream("configmap.yaml");

		ConfigMap leftConfigMap = Serialization.unmarshal(leftConfigMapStream, ConfigMap.class);
		ConfigMap rightConfigMap = Serialization.unmarshal(rightConfigMapStream, ConfigMap.class);
		ConfigMap configMap = Serialization.unmarshal(configMapStream, ConfigMap.class);

		replaceConfigMap(client, leftConfigMap, "left");
		replaceConfigMap(client, rightConfigMap, "right");
		replaceConfigMap(client, configMap, "default");
	}

	static void replaceConfigMap(KubernetesClient client, ConfigMap configMap, String namespace) {
		client.configMaps().inNamespace(namespace).resource(configMap).createOrReplace();
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

	static void patchFour(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_FOUR, POD_LABELS);
	}

	static void patchFive(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_FIVE, POD_LABELS);
	}

	static void patchSix(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_SIX, POD_LABELS);
	}

	static void patchSeven(Util util, String dockerImage, String deploymentName, String namespace) {
		util.patchWithReplace(dockerImage, deploymentName, namespace, BODY_SEVEN, POD_LABELS);
	}

	static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(120, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

	static String logs(K3sContainer container, String appLabelValue) {
		try {
			String appPodName = container
					.execInContainer("sh", "-c",
							"kubectl get pods -l app=" + appLabelValue + " -o=name --no-headers | tr -d '\n'")
					.getStdout();

			Container.ExecResult execResult = container.execInContainer("sh", "-c",
					"kubectl logs " + appPodName.trim());
			return execResult.getStdout();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
