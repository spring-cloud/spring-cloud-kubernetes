package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class K8sPodLabelsAndAnnotationsSupplier implements Function<String, PodLabelsAndAnnotations> {

	private final CoreV1Api client;

	private final String namespace;

	private K8sPodLabelsAndAnnotationsSupplier(CoreV1Api client, String namespace) {
		this.client = client;
		this.namespace = namespace;
	}

	static K8sPodLabelsAndAnnotationsSupplier nonExternalName(CoreV1Api client, String namespace) {
		return new K8sPodLabelsAndAnnotationsSupplier(client, namespace);
	}

	static K8sPodLabelsAndAnnotationsSupplier externalName() {
		return new K8sPodLabelsAndAnnotationsSupplier(null, null);
	}

	@Override
	public PodLabelsAndAnnotations apply(String podName) {
		try {
			V1ObjectMeta meta = Optional.ofNullable(client.readNamespacedPod(podName, namespace, null))
				.map(V1Pod::getMetadata).orElse(new V1ObjectMeta());
			return new PodLabelsAndAnnotations(
				Optional.ofNullable(meta.getLabels()).orElse(Map.of()),
				Optional.ofNullable(meta.getAnnotations()).orElse(Map.of())
			);
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}

	}

}
