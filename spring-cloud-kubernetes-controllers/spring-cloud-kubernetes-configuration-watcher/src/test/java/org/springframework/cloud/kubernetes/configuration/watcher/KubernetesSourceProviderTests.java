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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KubernetesSourceProviderTests {

	@Test
	void serviceNamesFallsBackToMetadataNameWhenAnnotationsMissing() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().name("my-secret")).build();

		Set<String> serviceNames = KubernetesSourceProvider.serviceNames(secret,
				SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, List.of());

		assertThat(serviceNames).containsExactly("my-secret");
	}

	@Test
	void serviceNamesFallsBackToMetadataNameWhenAnnotationMissing() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta().name("my-secret").annotations(Map.of("a", "b")))
			.build();

		Set<String> serviceNames = KubernetesSourceProvider.serviceNames(secret,
				SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, List.of());

		assertThat(serviceNames).containsExactly("my-secret");
	}

	@Test
	void serviceNamesFallsBackToMetadataNameWhenAnnotationBlank() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta().name("my-secret")
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, "   ")))
			.build();

		Set<String> serviceNames = KubernetesSourceProvider.serviceNames(secret,
				SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, List.of());

		assertThat(serviceNames).containsExactly("my-secret");
	}

	@Test
	void serviceNamesSingleValue() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta()
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, "one-app")))
			.build();

		Set<String> serviceNames = KubernetesSourceProvider.serviceNames(secret,
				SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, List.of());

		assertThat(serviceNames).containsExactly("one-app");
	}

	@Test
	void serviceNamesMultipleValuesTrimmed() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta()
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, "one, two,  three ")))
			.build();

		Set<String> serviceNames = KubernetesSourceProvider.serviceNames(secret,
				SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, List.of());

		assertThat(serviceNames).containsExactlyInAnyOrder("one", "two", "three");
	}

	@Test
	void serviceLabelsEmptyWhenAnnotationsMissing() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta()).build();

		Map<String, String> serviceLabels = KubernetesSourceProvider.serviceLabels(secret,
				SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION);

		assertThat(serviceLabels).isEmpty();
	}

	@Test
	void serviceLabelsEmptyWhenAnnotationMissing() {
		V1Secret secret = new V1SecretBuilder().withMetadata(new V1ObjectMeta().annotations(Map.of("a", "b"))).build();

		Map<String, String> serviceLabels = KubernetesSourceProvider.serviceLabels(secret,
				SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION);

		assertThat(serviceLabels).isEmpty();
	}

	@Test
	void serviceLabelsEmptyWhenAnnotationBlank() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta()
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION, "   ")))
			.build();

		Map<String, String> serviceLabels = KubernetesSourceProvider.serviceLabels(secret,
				SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION);

		assertThat(serviceLabels).isEmpty();
	}

	@Test
	void serviceLabelsSinglePair() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta()
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION, "app=my-app")))
			.build();

		Map<String, String> serviceLabels = KubernetesSourceProvider.serviceLabels(secret,
				SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION);

		assertThat(serviceLabels).containsExactly(Map.entry("app", "my-app"));
	}

	@Test
	void serviceLabelsMultiplePairsTrimmed() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta().annotations(
					Map.of(SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION, "app=my-app, tier=backend ")))
			.build();

		Map<String, String> serviceLabels = KubernetesSourceProvider.serviceLabels(secret,
				SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION);

		assertThat(serviceLabels).containsExactlyInAnyOrderEntriesOf(Map.of("app", "my-app", "tier", "backend"));
	}

	@Test
	void kubernetesSourceForConfigMap() {
		V1ConfigMap configMap = new V1ConfigMapBuilder()
			.withMetadata(new V1ObjectMeta().name("my-configmap")
				.annotations(Map.of(ConfigMapKubernetesSource.CONFIGMAP_SERVICE_NAMES_ANNOTATION, "app-one,app-two",
						ConfigMapKubernetesSource.CONFIGMAP_SERVICE_LABELS_ANNOTATION, "app=my-app,tier=backend")))
			.build();

		KubernetesSource source = KubernetesSourceProvider.kubernetesSource(configMap, List.of());

		assertThat(source).isInstanceOf(ConfigMapKubernetesSource.class);
		assertThat(source.description()).isEqualTo("configmap");
		assertThat(source.serviceNames()).containsExactlyInAnyOrder("app-one", "app-two");
		assertThat(source.serviceLabels())
			.containsExactlyInAnyOrderEntriesOf(Map.of("app", "my-app", "tier", "backend"));
	}

	@Test
	void kubernetesSourceForSecret() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(
					new V1ObjectMeta().name("my-secret")
						.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, "app-one,app-two",
								SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION, "app=my-app,tier=backend")))
			.build();

		KubernetesSource source = KubernetesSourceProvider.kubernetesSource(secret, List.of());

		assertThat(source).isInstanceOf(SecretKubernetesSource.class);
		assertThat(source.description()).isEqualTo("secret");
		assertThat(source.serviceNames()).containsExactlyInAnyOrder("app-one", "app-two");
		assertThat(source.serviceLabels())
			.containsExactlyInAnyOrderEntriesOf(Map.of("app", "my-app", "tier", "backend"));
	}

	@Test
	void kubernetesSourceUnsupportedType() {
		KubernetesObject kubernetesObject = Mockito.mock(KubernetesObject.class);

		assertThatThrownBy(() -> KubernetesSourceProvider.kubernetesSource(kubernetesObject, List.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported KubernetesObject type");
	}

	@Test
	void configuredConfigMapAppsOverrideServiceNamesAnnotation() {
		V1ConfigMap configMap = new V1ConfigMapBuilder()
			.withMetadata(
					new V1ObjectMeta().name("my-configmap")
						.annotations(Map.of(ConfigMapKubernetesSource.CONFIGMAP_SERVICE_NAMES_ANNOTATION,
								"app-from-annotation")))
			.build();

		KubernetesSource source = KubernetesSourceProvider.kubernetesSource(configMap, List.of("app-from-property"));

		assertThat(source.serviceNames()).containsExactly("app-from-property");
	}

	@Test
	void configuredSecretAppsOverrideServiceNamesAnnotation() {
		V1Secret secret = new V1SecretBuilder()
			.withMetadata(new V1ObjectMeta().name("my-secret")
				.annotations(Map.of(SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION, "app-from-annotation")))
			.build();

		KubernetesSource source = KubernetesSourceProvider.kubernetesSource(secret, List.of("app-from-property"));

		assertThat(source.serviceNames()).containsExactly("app-from-property");
	}

}
