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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.CONFIG_MAP_LABEL;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.SECRET_APPS_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.SECRET_LABEL;

class WatcherUtilTests {

	@Test
	void isSpringCloudKubernetesConfigFalse() {
		V1ConfigMap configMap = new V1ConfigMapBuilder().withMetadata(new V1ObjectMeta().labels(Map.of())).build();
		boolean present = WatcherUtil.isSpringCloudKubernetes(configMap, CONFIG_MAP_LABEL);
		Assertions.assertFalse(present);
	}

	@Test
	void isSpringCloudKubernetesConfigTrue() {
		V1ConfigMap configMap = new V1ConfigMapBuilder()
				.withMetadata(new V1ObjectMeta().labels(Map.of(CONFIG_MAP_LABEL, "true"))).build();
		boolean present = WatcherUtil.isSpringCloudKubernetes(configMap, CONFIG_MAP_LABEL);
		Assertions.assertTrue(present);
	}

	@Test
	void isSpringCloudKubernetesSecretFalse() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().labels(Map.of())).build();
		boolean present = WatcherUtil.isSpringCloudKubernetes(secret, SECRET_LABEL);
		Assertions.assertFalse(present);
	}

	@Test
	void isSpringCloudKubernetesSecretTrue() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().labels(Map.of(SECRET_LABEL, "true")))
				.build();
		boolean present = WatcherUtil.isSpringCloudKubernetes(secret, SECRET_LABEL);
		Assertions.assertTrue(present);
	}

	@Test
	void labelsMissing() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta()).build();
		Map<String, String> res = WatcherUtil.labels(secret);
		Assertions.assertEquals(res.size(), 0);
	}

	@Test
	void labelsPresent() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().labels(Map.of("a", "b"))).build();
		Map<String, String> res = WatcherUtil.labels(secret);
		Assertions.assertEquals(res.size(), 1);
	}

	@Test
	void appsNoMetadata() {
		V1Secret secret = new V1SecretBuilder().build();
		Set<String> apps = WatcherUtil.apps(secret, SECRET_APPS_ANNOTATION);
		Assertions.assertEquals(apps.size(), 0);
	}

	@Test
	void appsNoAnnotations() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().annotations(Map.of())).build();
		Set<String> apps = WatcherUtil.apps(secret, SECRET_APPS_ANNOTATION);
		Assertions.assertEquals(apps.size(), 0);
	}

	@Test
	void appsAnnotationNotFound() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().annotations(Map.of("a", "b"))).build();
		Set<String> apps = WatcherUtil.apps(secret, SECRET_APPS_ANNOTATION);
		Assertions.assertEquals(apps.size(), 0);
	}

	@Test
	void appsSingleResult() {
		V1Secret secret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMeta().annotations(Map.of(SECRET_APPS_ANNOTATION, "one-app"))).build();
		Set<String> apps = WatcherUtil.apps(secret, SECRET_APPS_ANNOTATION);
		Assertions.assertEquals(apps.size(), 1);
		Assertions.assertEquals(apps.iterator().next(), "one-app");
	}

	@Test
	void appsMultipleResults() {
		V1Secret secret = new V1SecretBuilder()
				.withMetadata(new V1ObjectMeta().annotations(Map.of(SECRET_APPS_ANNOTATION, "one, two,  three ")))
				.build();
		Set<String> apps = WatcherUtil.apps(secret, SECRET_APPS_ANNOTATION);
		Assertions.assertEquals(apps.size(), 3);
		Assertions.assertTrue(apps.contains("one"));
		Assertions.assertTrue(apps.contains("two"));
		Assertions.assertTrue(apps.contains("three"));
	}

}
