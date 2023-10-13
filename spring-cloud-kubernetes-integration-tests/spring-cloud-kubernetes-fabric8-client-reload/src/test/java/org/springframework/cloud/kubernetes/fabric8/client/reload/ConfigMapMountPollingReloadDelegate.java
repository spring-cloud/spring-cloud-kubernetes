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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Assertions;

import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.reload.TestUtil.replaceConfigMap;

/**
 * @author wind57
 */
final class ConfigMapMountPollingReloadDelegate {

	/**
	 * <pre>
	 *     - we have "spring.config.import: kubernetes:,configtree:/tmp/", which means we will 'locate' property sources
	 *       from config maps.
	 *     - the property above means that at the moment we will be searching for config maps that only
	 *       match the application name, in this specific test there is no such config map.
	 *     - what we will also read, is /tmp directory according to configtree rules.
	 *       As such, a property "props.key" (see TestUtil::BODY_SIX) will be in environment.
	 *
	 *     - we then change the config map content, wait for configuration watcher to pick up the change
	 *       and schedule a refresh event, based on http.
	 * </pre>
	 */
	static void testConfigMapMountPollingReload(KubernetesClient client, Util util) {
		WebClient webClient = TestUtil.builder().baseUrl("http://localhost/key-no-mount").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class)
				.retryWhen(TestUtil.retrySpec()).block();

		// we first read the initial value from the configmap
		Assertions.assertEquals("as-mount-initial", result);

		// replace data in configmap and wait for k8s to pick it up
		// our polling will detect that and restart the app
		InputStream configMapMountStream = util.inputStream("configmap-configtree.yaml");
		ConfigMap configMapMount = Serialization.unmarshal(configMapMountStream, ConfigMap.class);
		configMapMount.setData(Map.of("from.properties", "as-mount-changed"));
		// add label so that configuration-watcher picks this up
		Map<String, String> existingLabels = new HashMap<>(
				Optional.ofNullable(configMapMount.getMetadata().getLabels()).orElse(Map.of()));
		existingLabels.put("spring.cloud.kubernetes.config", "true");
		configMapMount.getMetadata().setLabels(existingLabels);

		// add annotation for which app to send the http event to
		Map<String, String> existingAnnotations = new HashMap<>(
				Optional.ofNullable(configMapMount.getMetadata().getAnnotations()).orElse(Map.of()));
		existingAnnotations.put("spring.cloud.kubernetes.configmap.apps",
				"spring-cloud-kubernetes-fabric8-client-reload");
		configMapMount.getMetadata().setAnnotations(existingAnnotations);
		TestUtil.replaceConfigMap(client, configMapMount, "default");

		await().timeout(Duration.ofSeconds(180)).until(() -> webClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(String.class).retryWhen(TestUtil.retrySpec()).block().equals("as-mount-changed"));

	}

}
