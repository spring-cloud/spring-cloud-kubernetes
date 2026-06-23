package org.springframework.cloud.kubernetes.configuration.watcher;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import org.springframework.core.log.LogAccessor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigMapKubernetesSource.CONFIGMAP_SERVICE_NAMES_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigMapKubernetesSource.CONFIGMAP_SERVICE_LABELS_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.SecretKubernetesSource.SECRET_SERVICE_NAMES_ANNOTATION;
import static org.springframework.cloud.kubernetes.configuration.watcher.SecretKubernetesSource.SECRET_SERVICE_LABELS_ANNOTATION;


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

	private static Map<String, String> serviceLabels(KubernetesObject kubernetesObject, String annotationName) {
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

	private static Set<String> serviceNames(KubernetesObject kubernetesObject, String annotationName) {

		// mutable on purpose
		Set<String> serviceNames = new HashSet<>(1);
		Map<String, String> annotations = annotations(kubernetesObject);

		if (annotations.isEmpty()) {
			LOG.debug(() -> annotationName + " not present (empty data)");
			return serviceNames;
		}

		String annotationValue = annotations.get(annotationName);

		if (annotationValue == null) {
			LOG.debug(() -> annotationName + " not present (missing in annotations)");
			return serviceNames;
		}

		if (annotationValue.isBlank()) {
			LOG.debug(() -> annotationValue + " not present (blanks only)");
			return serviceNames;
		}

		Set<String> serviceNamesFromAnnotation =
			Arrays.stream(annotationValue.split(",")).map(String::trim).collect(Collectors.toSet());

		if (serviceNamesFromAnnotation.isEmpty()) {
			serviceNames.add(kubernetesObject.getMetadata().getName());
		}

		return serviceNamesFromAnnotation;
	}

	private static Map<String, String> annotations(KubernetesObject kubernetesObject) {
		return Optional.ofNullable(kubernetesObject.getMetadata())
			.map(V1ObjectMeta::getAnnotations)
			.orElse(Map.of());
	}

}
