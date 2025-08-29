/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.k8s.client.reload.it;

import java.time.Duration;
import java.util.Map;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.k8s.client.reload.App;
import org.springframework.cloud.kubernetes.k8s.client.reload.RightProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import static org.awaitility.Awaitility.await;

/**
 * @author wind57
 */
@SpringBootTest(classes = { App.class, K8sClientConfigMapEventTriggeredIT.TestConfig.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes", "spring.profiles.active=two",
		"spring.cloud.bootstrap.enabled=true",
		"logging.level.org.springframework.cloud.kubernetes.client.config.reload=debug" })
class K8sClientConfigMapEventTriggeredIT extends K8sClientReloadBase {

	private static final MockedStatic<KubernetesClientUtils> KUBERNETES_CLIENT_UTILS_MOCKED_STATIC = Mockito
		.mockStatic(KubernetesClientUtils.class);

	private static V1ConfigMap rightConfigMap;

	@Autowired
	private RightProperties rightProperties;

	@Autowired
	private CoreV1Api coreV1Api;

	@BeforeAll
	static void beforeAllLocal() {

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient)
			.thenReturn(apiClient());

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC
			.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.anyString(), Mockito.anyString(),
					Mockito.any(KubernetesNamespaceProvider.class)))
			.thenReturn(NAMESPACE_RIGHT);

		util.createNamespace(NAMESPACE_RIGHT);
		rightConfigMap = (V1ConfigMap) util.yaml("right-configmap.yaml");
		util.createAndWait(NAMESPACE_RIGHT, rightConfigMap, null);
	}

	@AfterAll
	static void afterAllLocal() {
		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.close();
		util.deleteAndWait(NAMESPACE_RIGHT, rightConfigMap, null);
		util.deleteNamespace(NAMESPACE_RIGHT);
	}

	/**
	 * <pre>
	 *     - there is one namespace : right
	 *     - namespaces has one configmap
	 *     - we watch this namespace and make a change in the configmap
	 *     - as such, event is triggered and we see the updated value
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		assertReloadLogStatements("added configmap informer for namespace : right with filter : null",
				"added secret informer for namespace", output);

		Assertions.assertThat(rightProperties.getValue()).isEqualTo("right-initial");

		// then deploy a new version of right-configmap
		V1ConfigMap rightConfigMapAfterChange = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMeta().namespace(NAMESPACE_RIGHT).name("right-configmap"))
			.withData(Map.of("right.value", "right-after-change"))
			.build();

		replaceConfigMap(coreV1Api, rightConfigMapAfterChange);

		await().atMost(Duration.ofSeconds(60))
			.pollDelay(Duration.ofSeconds(1))
			.until(() -> output.getOut().contains("ConfigMap right-configmap was updated in namespace right"));

		await().atMost(Duration.ofSeconds(60))
			.pollInterval(Duration.ofSeconds(1))
			.until(() -> rightProperties.getValue().equals("right-after-change"));
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		ApiClient client() {
			return apiClient();
		}

	}

}
