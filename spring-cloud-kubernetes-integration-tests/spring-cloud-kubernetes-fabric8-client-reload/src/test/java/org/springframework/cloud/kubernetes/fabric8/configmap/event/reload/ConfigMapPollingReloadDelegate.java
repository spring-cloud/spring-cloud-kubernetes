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

package org.springframework.cloud.kubernetes.fabric8.configmap.event.reload;

import java.time.Duration;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Assertions;

import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.reactive.function.client.WebClient;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.builder;
import static org.springframework.cloud.kubernetes.fabric8.configmap.event.reload.TestUtil.retrySpec;

/**
 * @author wind57
 */
final class ConfigMapPollingReloadDelegate {

	static void testConfigMapPollingReload(KubernetesClient client) {
		WebClient webClient = builder().baseUrl("http://localhost/key").build();
		String result = webClient.method(HttpMethod.GET).retrieve().bodyToMono(String.class).retryWhen(retrySpec())
				.block();

		// we first read the initial value from the configmap
		Assertions.assertEquals("initial", result);

		// then deploy a new version of configmap
		// since we poll and have reload in place, the new property must be visible
		ConfigMap map = new ConfigMapBuilder()
				.withMetadata(new ObjectMetaBuilder().withNamespace("default").withName("poll-reload").build())
				.withData(Map.of("application.properties", "from.properties.key=after-change")).build();

		client.configMaps().inNamespace("default").resource(map).createOrReplace();

		await().ignoreException(HttpServerErrorException.BadGateway.class)
				.timeout(Duration.ofSeconds(120)).until(() -> webClient.method(HttpMethod.GET).retrieve()
				.bodyToMono(String.class).retryWhen(retrySpec()).block().equals("after-change"));

	}

}
