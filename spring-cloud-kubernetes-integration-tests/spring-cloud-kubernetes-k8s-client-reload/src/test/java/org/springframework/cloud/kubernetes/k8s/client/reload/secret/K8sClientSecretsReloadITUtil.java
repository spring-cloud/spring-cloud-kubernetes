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

package org.springframework.cloud.kubernetes.k8s.client.reload.secret;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.kubernetes.integration.tests.commons.native_client.Util.patchWithReplace;

/**
 * @author wind57
 */
final class K8sClientSecretsReloadITUtil {

	private static final Map<String, String> POD_LABELS = Map.of("app", "spring-k8s-client-reload");

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
									"name": "SPRING_CLOUD_KUBERNETES_CONFIG_ENABLED",
									"value": "FALSE"
								},
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

	private K8sClientSecretsReloadITUtil() {

	}

	static WebClient.Builder builder() {
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create()));
	}

	static RetryBackoffSpec retrySpec() {
		return Retry.fixedDelay(60, Duration.ofSeconds(2)).filter(Objects::nonNull);
	}

	static void patchOne(String deploymentName, String namespace, String imageName) {
		patchWithReplace(imageName, deploymentName, namespace, BODY_ONE, POD_LABELS);
	}

}
