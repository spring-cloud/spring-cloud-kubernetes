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

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;

import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigMapKubernetesSource.CONFIGMAP_SERVICE_LABELS_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigMapKubernetesSource.CONFIGMAP_SERVICE_NAMES_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION;

/**
 * Creates {@link KubernetesSource} instances from Kubernetes resource annotations on
 * ConfigMaps and Secrets.
 *
 * <p>
 * This class reads the supported watcher annotations and turns them into a parsed source
 * model used by the refresh flow.
 *
 * <p>
 * If no service-names annotation is present, the resource name ({@code .metadata.name})
 * is used as the default target.
 *
 * @author wind57
 */
final class KubernetesSourceProvider {

	private static final LogAccessor LOG = new LogAccessor(KubernetesSource.class);

	private KubernetesSourceProvider() {

	}

	static KubernetesSource kubernetesSource(KubernetesObject kubernetesObject) {

		if (kubernetesObject instanceof V1ConfigMap) {
			Set<String> serviceNames = serviceNames(kubernetesObject, CONFIGMAP_SERVICE_NAMES_ANNOTATION);
			Map<String, String> serviceLabels = serviceLabels(kubernetesObject, CONFIGMAP_SERVICE_LABELS_ANNOTATION);
			return new ConfigMapKubernetesSource(serviceNames, serviceLabels);
		}
		if (kubernetesObject instanceof V1Secret) {
			Set<String> serviceNames = serviceNames(kubernetesObject, SECRET_SERVICE_NAMES_ANNOTATION);
			Map<String, String> serviceLabels = serviceLabels(kubernetesObject, SECRET_SERVICE_LABELS_ANNOTATION);
			return new SecretKubernetesSource(serviceNames, serviceLabels);
		}

		throw new IllegalArgumentException("Unsupported KubernetesObject type: " + kubernetesObject.getClass());
	}

	/**
	 * Extract target service labels from the provided annotation.
	 *
	 * <p>
	 * The annotation value is expected to be a comma-separated list of {@code key=value}
	 * pairs, for example {@code app=my-app,tier=backend}.
	 *
	 * <p>
	 * If the annotation is not present or contains only blanks, no labels are extracted.
	 */
	static Map<String, String> serviceLabels(KubernetesObject kubernetesObject, String annotationName) {
		Map<String, String> annotations = annotations(kubernetesObject);

		if (annotations.isEmpty()) {
			LOG.debug(() -> annotationName + " not present (empty data)");
			return Map.of();
		}

		String annotationValue = annotations.get(annotationName);

		if (annotationValue == null) {
			LOG.debug(() -> annotationName + " not present (missing in annotations)");
			return Map.of();
		}

		if (annotationValue.isBlank()) {
			LOG.debug(() -> annotationValue + " not present (blanks only)");
			return Map.of();
		}

		return Arrays.stream(annotationValue.split(","))
			.map(String::trim)
			.map(s -> s.split("="))
			.filter(arr -> arr.length == 2)
			.collect(Collectors.toMap(arr -> arr[0].trim(), arr -> arr[1].trim()));
	}

	/**
	 * Extract target service names from the provided annotation.
	 *
	 * <p>
	 * The annotation value is expected to be a comma-separated list of service names, for
	 * example {@code app-one,app-two}.
	 *
	 * <p>
	 * If the annotation is not present or contains only blanks, the Kubernetes resource
	 * name ({@code .metadata.name}) is used as the default target.
	 */
	static Set<String> serviceNames(KubernetesObject kubernetesObject, String annotationName) {

		String metadataName = kubernetesObject.getMetadata().getName();
		Map<String, String> annotations = annotations(kubernetesObject);

		if (annotations.isEmpty()) {
			LOG.debug(() -> annotationName + " not present (empty data)");
			return Set.of(metadataName);
		}

		String annotationValue = annotations.get(annotationName);

		if (annotationValue == null) {
			LOG.debug(() -> annotationName + " not present (missing in annotations)");
			return Set.of(metadataName);
		}

		if (annotationValue.isBlank()) {
			LOG.debug(() -> annotationValue + " not present (blanks only)");
			return Set.of(metadataName);
		}

		return Arrays.stream(annotationValue.split(",")).map(String::trim).collect(Collectors.toSet());

	}

	private static Map<String, String> annotations(KubernetesObject kubernetesObject) {
		return Optional.ofNullable(kubernetesObject.getMetadata()).map(V1ObjectMeta::getAnnotations).orElse(Map.of());
	}

}
