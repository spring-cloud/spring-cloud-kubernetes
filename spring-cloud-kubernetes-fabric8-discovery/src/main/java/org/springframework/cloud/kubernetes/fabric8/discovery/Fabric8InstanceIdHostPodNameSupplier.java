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
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Service;

import org.springframework.cloud.kubernetes.commons.discovery.InstanceIdHostPodName;

/**
 * computes instanceId, host and podName. All needed when calculating ServiceInstance.
 *
 * @author wind57
 */
final class Fabric8InstanceIdHostPodNameSupplier implements Supplier<InstanceIdHostPodName> {

	private final EndpointAddress endpointAddress;

	private final Service service;

	private Fabric8InstanceIdHostPodNameSupplier(EndpointAddress endpointAddress, Service service) {
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
	static Fabric8InstanceIdHostPodNameSupplier nonExternalName(EndpointAddress endpointAddress, Service service) {
		return new Fabric8InstanceIdHostPodNameSupplier(endpointAddress, service);
	}

	/**
	 * to be used when .spec.type of the Service is == 'ExternalName'.
	 */
	static Fabric8InstanceIdHostPodNameSupplier externalName(Service service) {
		return new Fabric8InstanceIdHostPodNameSupplier(null, service);
	}

	// instanceId is usually the pod-uid as seen in the .metadata.uid
	private String instanceId() {
		return Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef).map(ObjectReference::getUid)
				.orElseGet(() -> service.getMetadata().getUid());
	}

	private String host() {
		return Optional.ofNullable(endpointAddress).map(EndpointAddress::getIp)
				.orElseGet(() -> service.getSpec().getExternalName());
	}

	private String podName() {
		return Optional.ofNullable(endpointAddress).map(EndpointAddress::getTargetRef)
				.filter(objectReference -> "Pod".equals(objectReference.getKind())).map(ObjectReference::getName)
				.orElse(null);
	}

}
