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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Map;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author wind57
 */
class KubernetesClientPodLabelsAndAnnotationsSupplierTests {

	private static final String NAMESPACE = "spring-k8s";

	private static final String POD_NAME = "my-pod";

	private final CoreV1Api coreV1Api = mock(CoreV1Api.class);

	@AfterEach
	void afterEach() {
		Mockito.reset(coreV1Api);
	}

	@Test
	void noObjetMeta() throws Exception {
		CoreV1Api.APIreadNamespacedPodRequest request = mock(CoreV1Api.APIreadNamespacedPodRequest.class);
		when(request.execute())
			.thenReturn(new V1PodBuilder().withMetadata(new V1ObjectMetaBuilder().withName(POD_NAME).build()).build());
		when(coreV1Api.readNamespacedPod(POD_NAME, NAMESPACE)).thenReturn(request);

		PodLabelsAndAnnotations result = KubernetesClientPodLabelsAndAnnotationsSupplier
			.nonExternalName(coreV1Api, NAMESPACE)
			.apply(POD_NAME);
		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.labels()).isEmpty();
		Assertions.assertThat(result.annotations()).isEmpty();
	}

	@Test
	void labelsAndAnnotationsPresent() throws Exception {
		CoreV1Api.APIreadNamespacedPodRequest request = mock(CoreV1Api.APIreadNamespacedPodRequest.class);
		when(request.execute()).thenReturn(
				new V1PodBuilder()
					.withMetadata(new V1ObjectMetaBuilder().withName(POD_NAME)
						.withLabels(Map.of("a", "b"))
						.withAnnotations(Map.of("c", "d"))
						.build())
					.build());
		when(coreV1Api.readNamespacedPod(POD_NAME, NAMESPACE)).thenReturn(request);

		PodLabelsAndAnnotations result = KubernetesClientPodLabelsAndAnnotationsSupplier
			.nonExternalName(coreV1Api, NAMESPACE)
			.apply(POD_NAME);
		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.labels()).isEqualTo(Map.of("a", "b"));
		Assertions.assertThat(result.annotations()).isEqualTo(Map.of("c", "d"));
	}

}
