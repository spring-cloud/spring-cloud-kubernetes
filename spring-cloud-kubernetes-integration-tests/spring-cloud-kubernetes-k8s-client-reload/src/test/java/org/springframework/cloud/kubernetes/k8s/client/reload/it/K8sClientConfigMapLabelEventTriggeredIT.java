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

import java.util.Map;

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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.cloud.kubernetes.client.KubernetesClientUtils;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.integration.tests.commons.Awaitilities;
import org.springframework.cloud.kubernetes.integration.tests.commons.k3s.NativeClientIntegrationTest;
import org.springframework.cloud.kubernetes.integration.tests.commons.native_client.NativeClientKubernetesFixture;
import org.springframework.cloud.kubernetes.k8s.client.reload.App;
import org.springframework.cloud.kubernetes.k8s.client.reload.RightProperties;
import org.springframework.cloud.kubernetes.k8s.client.reload.RightWithLabelsProperties;
import org.springframework.test.context.TestPropertySource;

/**
 * @author wind57
 */
@SpringBootTest(classes = { App.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.main.cloud-platform=kubernetes", "spring.profiles.active=three",
		"spring.cloud.bootstrap.enabled=true",
		"logging.level.org.springframework.cloud.kubernetes.client.config.reload=debug" })
@NativeClientIntegrationTest(namespaces = "right")
class K8sClientConfigMapLabelEventTriggeredIT extends K8sClientReloadBase {

	private static final MockedStatic<KubernetesClientUtils> KUBERNETES_CLIENT_UTILS_MOCKED_STATIC = Mockito
		.mockStatic(KubernetesClientUtils.class);

	private static V1ConfigMap rightConfigMap;

	private static V1ConfigMap rightConfigMapWithLabel;

	@Autowired
	private RightProperties rightProperties;

	@Autowired
	private RightWithLabelsProperties rightWithLabelsProperties;

	@Autowired
	private CoreV1Api coreV1Api;

	@BeforeAll
	static void beforeAllLocal(NativeClientKubernetesFixture fixture) {

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.when(KubernetesClientUtils::createApiClientForInformerClient)
			.thenReturn(apiClient());

		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC
			.when(() -> KubernetesClientUtils.getApplicationNamespace(Mockito.anyString(), Mockito.anyString(),
					Mockito.any(KubernetesNamespaceProvider.class)))
			.thenReturn("right");

		rightConfigMap = fixture.yaml("right-configmap.yaml", V1ConfigMap.class);
		rightConfigMapWithLabel = fixture.yaml("right-configmap-with-label.yaml", V1ConfigMap.class);
		fixture.createAndWait("right", rightConfigMap, null);
		fixture.createAndWait("right", rightConfigMapWithLabel, null);
	}

	@AfterAll
	static void afterAllLocal(NativeClientKubernetesFixture fixture) {
		KUBERNETES_CLIENT_UTILS_MOCKED_STATIC.close();
		fixture.deleteAndWait("right", rightConfigMap, null);
		fixture.deleteAndWait("right", rightConfigMapWithLabel, null);
	}

	/**
	 * <pre>
	 *     - we have one namespace : 'right'.
	 *     - it has two configmaps : 'right-configmap' and 'right-configmap-with-label'
	 *     - we watch 'right' namespace, but enable tagging; which means that only
	 *       right-configmap-with-label triggers a change.
	 * </pre>
	 */
	@Test
	void test(CapturedOutput output) {

		assertReloadLogStatements(
				"configmap informer for namespace : "
						+ "right with labels : {spring.cloud.kubernetes.config.informer.enabled=true}",
				"secret informer for namespace", output);

		// read the initial value from the right-configmap
		Assertions.assertThat(rightProperties.getValue()).isEqualTo("right-initial");

		// read the initial value from the right-configmap-with-label
		Assertions.assertThat(rightWithLabelsProperties.getValue()).isEqualTo("right-with-label-initial");

		// then deploy a new version of right-configmap
		V1ConfigMap rightConfigMapAfterChange = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMeta().namespace("right")
				.name("right-configmap")
				.labels(Map.of("spring.cloud.kubernetes.config.informer.enabled", "true")))
			.withData(Map.of("right.value", "right-after-change"))
			.build();

		replaceConfigMap(coreV1Api, rightConfigMapAfterChange);

		Awaitilities.awaitUntil(10, 1000, () -> rightProperties.getValue().equals("right-after-change"));

		// then deploy a new version of right-configmap-with-label
		// but only add a label, this does not trigger a refresh
		V1ConfigMap rightWithLabelConfigMap = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMeta().namespace("right")
				.name("right-configmap-with-label")
				.labels(Map.of("spring.cloud.kubernetes.config.informer.enabled", "true", "custom.label",
						"spring-k8s")))
			.withData(Map.of("right.with.label.value", "right-with-label-initial"))
			.build();

		replaceConfigMap(coreV1Api, rightWithLabelConfigMap);

		Awaitilities.awaitUntil(60, 1000,
				() -> output.getOut().contains("data in configmap has not changed, will not reload"));
		Awaitilities.awaitUntil(60, 1000,
				() -> rightWithLabelsProperties.getValue().equals("right-with-label-initial"));

		// then deploy a new version of right-configmap-with-label
		// that changes data also
		V1ConfigMap rightWithLabelConfigMapAfterChange = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMeta().namespace("right")
				.name("right-configmap-with-label")
				.labels(Map.of("spring.cloud.kubernetes.config.informer.enabled", "true")))
			.withData(Map.of("right.with.label.value", "right-with-label-after-change"))
			.build();

		replaceConfigMap(coreV1Api, rightWithLabelConfigMapAfterChange);

		Awaitilities.awaitUntil(60, 1000,
				() -> output.getOut().contains("ConfigMap right-configmap-with-label was updated in namespace right"));
		Awaitilities.awaitUntil(60, 1000,
				() -> rightWithLabelsProperties.getValue().equals("right-with-label-after-change"));
	}

}
