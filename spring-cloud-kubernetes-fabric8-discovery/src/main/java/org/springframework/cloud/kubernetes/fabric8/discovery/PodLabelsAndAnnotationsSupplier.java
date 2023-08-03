package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

import java.util.Optional;
import java.util.function.Function;

final class PodLabelsAndAnnotationsSupplier implements Function<String, PodLabelsAndAnnotations> {

	private final KubernetesClient client;

	private final String namespace;

	PodLabelsAndAnnotationsSupplier(KubernetesClient client, String namespace) {
		this.client = client;
		this.namespace = namespace;
	}


	@Override
	public PodLabelsAndAnnotations apply(String podName) {
		ObjectMeta metadata = Optional
			.ofNullable(client.pods().inNamespace(namespace).withName(podName).get())
			.map(Pod::getMetadata).orElse(new ObjectMeta());
		return new PodLabelsAndAnnotations(metadata.getLabels(), metadata.getAnnotations());
	}
}
