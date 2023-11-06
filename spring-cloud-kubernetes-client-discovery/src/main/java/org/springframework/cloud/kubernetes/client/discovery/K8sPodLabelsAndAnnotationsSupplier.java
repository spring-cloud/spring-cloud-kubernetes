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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.PodLabelsAndAnnotations;
import org.springframework.core.log.LogAccessor;

/**
 * @author wind57
 */
final class K8sPodLabelsAndAnnotationsSupplier implements Function<String, PodLabelsAndAnnotations> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(K8sPodLabelsAndAnnotationsSupplier.class));

	private final CoreV1Api coreV1Api;

	private final String namespace;

	private K8sPodLabelsAndAnnotationsSupplier(CoreV1Api coreV1Api, String namespace) {
		this.coreV1Api = coreV1Api;
		this.namespace = namespace;
	}

	/**
	 * to be used when .spec.type of the Service is != 'ExternalName'.
	 */
	static K8sPodLabelsAndAnnotationsSupplier nonExternalName(CoreV1Api coreV1Api, String namespace) {
		return new K8sPodLabelsAndAnnotationsSupplier(coreV1Api, namespace);
	}

	/**
	 * to be used when .spec.type of the Service is == 'ExternalName'.
	 */
	static K8sPodLabelsAndAnnotationsSupplier externalName() {
		return new K8sPodLabelsAndAnnotationsSupplier(null, null);
	}

	@Override
	public PodLabelsAndAnnotations apply(String podName) {

		V1ObjectMeta objectMeta;

		try {
			objectMeta = Optional.ofNullable(coreV1Api.readNamespacedPod(podName, namespace, null).getMetadata())
					.orElse(new V1ObjectMetaBuilder().withLabels(Map.of()).withAnnotations(Map.of()).build());
		}
		catch (ApiException e) {
			LOG.warn(e, "Could not get pod metadata");
			objectMeta = new V1ObjectMetaBuilder().withLabels(Map.of()).withAnnotations(Map.of()).build();
		}

		return new PodLabelsAndAnnotations(Optional.ofNullable(objectMeta.getLabels()).orElse(Map.of()),
				Optional.ofNullable(objectMeta.getAnnotations()).orElse(Map.of()));
	}

}
