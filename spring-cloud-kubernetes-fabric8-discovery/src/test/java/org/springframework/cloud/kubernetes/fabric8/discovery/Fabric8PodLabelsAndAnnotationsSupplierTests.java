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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

/**
 * @author wind57
 */
@EnableKubernetesMockClient(crud = true, https = false)
class Fabric8PodLabelsAndAnnotationsSupplierTests {

	private static final String NAMESPACE = "spring-k8s";

	private static final String POD_NAME = "my-pod";

	private static KubernetesClient client;

	@AfterEach
	void afterEach() {
		client.pods().inAnyNamespace().delete();
	}

	@Test
	void noObjetMeta() {
		client.pods().inNamespace(NAMESPACE)
				.resource(new PodBuilder().withMetadata(new ObjectMetaBuilder().withName(POD_NAME).build()).build())
				.create();

		PodLabelsAndAnnotations result = Fabric8PodLabelsAndAnnotationsSupplier.nonExternalName(client, NAMESPACE)
				.apply(POD_NAME);
		Assertions.assertNotNull(result);
		Assertions.assertTrue(result.labels().isEmpty());
		Assertions.assertTrue(result.annotations().isEmpty());
	}

	@Test
	void labelsAndAnnotationsPresent() {
		client.pods().inNamespace(NAMESPACE).resource(new PodBuilder().withMetadata(new ObjectMetaBuilder()
				.withName(POD_NAME).withLabels(Map.of("a", "b")).withAnnotations(Map.of("c", "d")).build()).build())
				.create();

		PodLabelsAndAnnotations result = Fabric8PodLabelsAndAnnotationsSupplier.nonExternalName(client, NAMESPACE)
				.apply(POD_NAME);
		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.labels(), Map.of("a", "b"));
		Assertions.assertEquals(result.annotations(), Map.of("c", "d"));
	}

}
