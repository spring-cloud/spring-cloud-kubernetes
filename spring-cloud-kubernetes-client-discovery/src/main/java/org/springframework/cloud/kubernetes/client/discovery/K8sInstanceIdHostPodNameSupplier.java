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

import java.util.Optional;
import java.util.function.Supplier;

import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Service;

import org.springframework.cloud.kubernetes.commons.discovery.InstanceIdHostPodName;

/**
 * @author wind57
 */
final class K8sInstanceIdHostPodNameSupplier implements Supplier<InstanceIdHostPodName> {

	private final V1EndpointAddress endpointAddress;

	private final V1Service service;

	private K8sInstanceIdHostPodNameSupplier(V1EndpointAddress endpointAddress, V1Service service) {
		this.endpointAddress = endpointAddress;
		this.service = service;
	}

	@Override
	public InstanceIdHostPodName get() {
		return new InstanceIdHostPodName(instanceId(), host(), podName());
	}

	/**
	 * to be used when .spec.type of the Service is != 'ExternalName'.
	 */
	static K8sInstanceIdHostPodNameSupplier nonExternalName(V1EndpointAddress endpointAddress, V1Service service) {
		return new K8sInstanceIdHostPodNameSupplier(endpointAddress, service);
	}

	/**
	 * to be used when .spec.type of the Service is == 'ExternalName'.
	 */
	static K8sInstanceIdHostPodNameSupplier externalName(V1Service service) {
		return new K8sInstanceIdHostPodNameSupplier(null, service);
	}

	// instanceId is usually the pod-uid as seen in the .metadata.uid
	private String instanceId() {
		return Optional.ofNullable(endpointAddress).map(V1EndpointAddress::getTargetRef).map(V1ObjectReference::getUid)
				.orElseGet(() -> service.getMetadata().getUid());
	}

	private String host() {
		return Optional.ofNullable(endpointAddress).map(V1EndpointAddress::getIp)
				.orElseGet(() -> service.getSpec().getExternalName());
	}

	private String podName() {
		return Optional.ofNullable(endpointAddress).map(V1EndpointAddress::getTargetRef)
				.filter(objectReference -> "Pod".equals(objectReference.getKind())).map(V1ObjectReference::getName)
				.orElse(null);
	}

}
