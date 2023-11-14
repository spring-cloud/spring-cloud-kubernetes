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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

/**
 * @author wind57
 */
class K8sPodLabelsAndAnnotationsSupplierTests {

	private static final String NAMESPACE = "spring-k8s";

	private static final String POD_NAME = "my-pod";

	private final CoreV1Api coreV1Api = Mockito.mock(CoreV1Api.class);

	@AfterEach
	void afterEach() {
		Mockito.reset(coreV1Api);
	}

	@Test
	void noObjetMeta() throws Exception {

		Mockito.when(coreV1Api.readNamespacedPod(POD_NAME, NAMESPACE, null)).thenReturn(
				new V1PodBuilder().withMetadata(new V1ObjectMetaBuilder().withName(POD_NAME).build()).build());

		PodLabelsAndAnnotations result = K8sPodLabelsAndAnnotationsSupplier.nonExternalName(coreV1Api, NAMESPACE)
				.apply(POD_NAME);
		Assertions.assertNotNull(result);
		Assertions.assertTrue(result.labels().isEmpty());
		Assertions.assertTrue(result.annotations().isEmpty());
	}

	@Test
	void labelsAndAnnotationsPresent() throws Exception {

		Mockito.when(coreV1Api.readNamespacedPod(POD_NAME, NAMESPACE, null))
				.thenReturn(new V1PodBuilder().withMetadata(new V1ObjectMetaBuilder().withName(POD_NAME)
						.withLabels(Map.of("a", "b")).withAnnotations(Map.of("c", "d")).build()).build());

		PodLabelsAndAnnotations result = K8sPodLabelsAndAnnotationsSupplier.nonExternalName(coreV1Api, NAMESPACE)
				.apply(POD_NAME);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.labels(), Map.of("a", "b"));
		Assertions.assertEquals(result.annotations(), Map.of("c", "d"));
	}

}
