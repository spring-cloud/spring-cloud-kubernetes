package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

import java.util.Map;

class K8sPodLabelsAndAnnotationsSupplierTests {

	private static final String NAMESPACE = "spring-k8s";

	private static final String POD_NAME = "my-pod";

	private final CoreV1Api coreV1Api = Mockito.mock(CoreV1Api.class);

	@AfterEach
	void afterEach() {
		Mockito.reset(coreV1Api);
	}

	@Test
	void noObjectMeta() throws Exception{
		Mockito.when(coreV1Api.readNamespacedPod(POD_NAME, NAMESPACE, null))
				.thenReturn(new V1Pod().metadata(new V1ObjectMeta().name(POD_NAME)));

		PodLabelsAndAnnotations result = K8sPodLabelsAndAnnotationsSupplier.nonExternalName(coreV1Api, NAMESPACE)
			.apply(POD_NAME);
		Assertions.assertNotNull(result);
		Assertions.assertTrue(result.labels().isEmpty());
		Assertions.assertTrue(result.annotations().isEmpty());
	}

	@Test
	void labelsAndAnnotationsPresent() throws Exception {
		Mockito.when(coreV1Api.readNamespacedPod(POD_NAME, NAMESPACE, null))
			.thenReturn(new V1Pod().metadata(new V1ObjectMeta().name(POD_NAME).labels(Map.of("a", "b"))
				.annotations(Map.of("c", "d"))));

		PodLabelsAndAnnotations result = K8sPodLabelsAndAnnotationsSupplier.nonExternalName(coreV1Api, NAMESPACE)
			.apply(POD_NAME);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.labels(), Map.of("a", "b"));
		Assertions.assertEquals(result.annotations(), Map.of("c", "d"));
	}

}
