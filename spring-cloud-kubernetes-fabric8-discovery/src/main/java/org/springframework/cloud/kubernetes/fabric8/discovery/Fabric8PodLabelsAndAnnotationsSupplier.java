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

import java.util.Optional;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;

/**
 * A way to get labels and annotations from a podName.
 *
 * @author wind57
 */
final class Fabric8PodLabelsAndAnnotationsSupplier implements Function<String, PodLabelsAndAnnotations> {

	private final KubernetesClient client;

	private final String namespace;

	private Fabric8PodLabelsAndAnnotationsSupplier(KubernetesClient client, String namespace) {
		this.client = client;
		this.namespace = namespace;
	}

	/**
	 * to be used when .spec.type of the Service is != 'ExternalName'.
	 */
	static Fabric8PodLabelsAndAnnotationsSupplier nonExternalName(KubernetesClient client, String namespace) {
		return new Fabric8PodLabelsAndAnnotationsSupplier(client, namespace);
	}

	/**
	 * to be used when .spec.type of the Service is == 'ExternalName'.
	 */
	static Fabric8PodLabelsAndAnnotationsSupplier externalName() {
		return new Fabric8PodLabelsAndAnnotationsSupplier(null, null);
	}

	@Override
	public PodLabelsAndAnnotations apply(String podName) {
		ObjectMeta metadata = Optional.ofNullable(client.pods().inNamespace(namespace).withName(podName).get())
				.map(Pod::getMetadata).orElse(new ObjectMeta());
		return new PodLabelsAndAnnotations(metadata.getLabels(), metadata.getAnnotations());
	}

}
